/**
 *  Copyright 2019 Martynas Jusevičius <martynas@atomgraph.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.atomgraph.linkeddatahub.resource.file;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.DCTerms;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.ResourceContext;
import com.sun.jersey.multipart.FormDataBodyPart;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Providers;
import com.atomgraph.core.MediaTypes;
import com.atomgraph.linkeddatahub.model.Service;
import com.atomgraph.linkeddatahub.server.model.impl.ClientUriInfo;
import com.atomgraph.linkeddatahub.client.DataManager;
import com.atomgraph.linkeddatahub.vocabulary.NFO;
import com.atomgraph.processor.util.Skolemizer;
import com.atomgraph.processor.util.TemplateCall;
import com.atomgraph.processor.vocabulary.DH;
import com.sun.jersey.api.client.Client;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriInfo;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.InfModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS resource that handles multipart file uploads.
 * 
 * @author Martynas Jusevičius {@literal <martynas@atomgraph.com>}
 */
public class Container extends com.atomgraph.linkeddatahub.server.model.impl.ResourceBase
{
    private static final Logger log = LoggerFactory.getLogger(Container.class);
    
    public Container(@Context UriInfo uriInfo, @Context ClientUriInfo clientUriInfo, @Context Request request, @Context MediaTypes mediaTypes, 
            @Context Service service, @Context com.atomgraph.linkeddatahub.apps.model.Application application,
            @Context Ontology ontology, @Context TemplateCall templateCall,
            @Context HttpHeaders httpHeaders, @Context ResourceContext resourceContext,
            @Context Client client,
            @Context HttpContext httpContext, @Context SecurityContext securityContext,
            @Context DataManager dataManager, @Context Providers providers,
            @Context Application system)
    {
        super(uriInfo, clientUriInfo, request, mediaTypes,
                service, application,
                ontology, templateCall,
                httpHeaders, resourceContext,
                client,
                httpContext, securityContext,
                dataManager, providers,
                system);
    }

    @Override
    public File writeFile(Resource resource, FormDataBodyPart bodyPart)
    {
        if (resource == null) throw new IllegalArgumentException("File Resource cannot be null");
        if (!resource.isURIResource()) throw new IllegalArgumentException("File Resource must have a URI");
        if (bodyPart == null) throw new IllegalArgumentException("FormDataBodyPart cannot be null");

        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            try (InputStream is = bodyPart.getEntityAs(InputStream.class);
                DigestInputStream dis = new DigestInputStream(is, md))
            {
                File tempFile = File.createTempFile("tmp", null);
                FileChannel destination = new FileOutputStream(tempFile).getChannel();
                destination.transferFrom(Channels.newChannel(dis), 0, 104857600);
                String sha1Hash = new BigInteger(1, dis.getMessageDigest().digest()).toString(16);
                if (log.isDebugEnabled()) log.debug("Wrote file: {} with SHA1 hash: {}", tempFile, sha1Hash);

                resource.removeAll(DH.slug).
                    addLiteral(DH.slug, sha1Hash).
                    addLiteral(FOAF.sha1, sha1Hash).
                    addProperty(DCTerms.format, com.atomgraph.linkeddatahub.MediaType.toResource(bodyPart.getMediaType()));
                URI sha1Uri = new Skolemizer(getOntology(),
                        getUriInfo().getBaseUriBuilder(), getUriInfo().getAbsolutePathBuilder()).
                        build(resource);
                if (log.isDebugEnabled()) log.debug("Renaming resource: {} to SHA1 based URI: {}", resource, sha1Uri);
                ResourceUtils.renameResource(resource, sha1Uri.toString());

                return super.writeFile(sha1Uri, getUriInfo().getBaseUri(), new FileInputStream(tempFile));

                /*
                PropertiesCredentials credentials = new PropertiesCredentials(getServletContext().getResourceAsStream("AwsCredentials.properties"));
                AmazonS3 s3 = new AmazonS3Client(credentials);

                if (!s3.doesBucketExist(getUriInfo().getBaseUri().getHost()))
                    createBucket(s3, getUriInfo().getBaseUri().getHost());

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(bodyPart.getMediaType().toString());
                metadata.setContentDisposition("attachment");
                //PutObjectRequest request = new PutObjectRequest(uri.getHost(), uri.getPath().substring(1), dis, metadata); // remove leading / from path

                //s3.putObject(request);
                */
            }
        }
        catch (NoSuchAlgorithmException ex)
        {
            if (log.isErrorEnabled()) log.error("SHA1 algorithm not found", ex);
        }
        catch (IOException ex)
        {
            if (log.isErrorEnabled()) log.error("File I/O error", ex);
        }
        
        return null;
    }

    //@Override
    public Resource getURIResource(InfModel infModel, Property property, Resource object) // return the document about the file, not the file itself
    {
        if (infModel == null) throw new IllegalArgumentException("Model cannot be null");
        if (property == null) throw new IllegalArgumentException("Property cannot be null");
        if (object == null) throw new IllegalArgumentException("Object Resource cannot be null");
        
        ResIterator it = infModel.listSubjectsWithProperty(property, object);

        try
        {
            while (it.hasNext())
            {
                Resource resource = it.next();

                if (resource.isURIResource() && !resource.hasProperty(property, NFO.FileDataObject)) return resource;
            }
        }
        finally
        {
            it.close();
        }
        
        return null;
    }
    
//    @Override
//    public Response get()
//    {
//        if (getClientUriInfo().getQueryParameters().containsKey(AC.uri.getLocalName()))
//        {
//            String uri = getClientUriInfo().getQueryParameters().getFirst(AC.uri.getLocalName()); // external URI resource
//            if (uri.startsWith("https://www.googleapis.com/drive/v3/files"))
//            {
//                ClientResponse cr = null;
//                try
//                {
//                    cr = getClient().resource(uri).
//                        accept(MediaType.APPLICATION_JSON_TYPE).
//                        get(ClientResponse.class);
//
//                    FileListBean fileListBean = cr.getEntity(FileListBean.class);
//
//                    if (fileListBean == null) return Response.noContent().build();
//                    else return Response.ok(fileListBean.getFiles().size()).build();
//                }
//                finally
//                {
//                    if (cr != null) cr.close();
//                }
//            }
//        }
//        
//        return super.get();
//    }
    
    /*
    public void createBucket(AmazonS3 s3, String bucketName)
    {
        s3.createBucket(bucketName);

        // set public read permissions to all files -- good idea?!
        Statement allowPublicRead = new Statement(Statement.Effect.Allow)
            .withPrincipals(Principal.AllUsers)
            .withActions(S3Actions.GetObject)
            .withResources(new S3ObjectResource(bucketName, "*"));

        Policy policy = new Policy().withStatements(allowPublicRead);
        s3.setBucketPolicy(bucketName, policy.toJson());
    }
*/

}