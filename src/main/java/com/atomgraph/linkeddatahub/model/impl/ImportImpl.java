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
package com.atomgraph.linkeddatahub.model.impl;

import com.atomgraph.linkeddatahub.client.DataManager;
import com.atomgraph.linkeddatahub.model.Import;
import com.atomgraph.linkeddatahub.vocabulary.APL;
import com.atomgraph.processor.util.Validator;
import java.util.List;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.vocabulary.RDFS;
import org.spinrdf.constraints.ConstraintViolation;
import org.spinrdf.constraints.ObjectPropertyPath;
import org.spinrdf.constraints.SimplePropertyPath;
import org.spinrdf.vocabulary.SP;
import org.spinrdf.vocabulary.SPIN;

/**
 *
 * @author Martynas Jusevičius {@literal <martynas@atomgraph.com>}
 */
public class ImportImpl extends ResourceImpl implements Import
{

    private DataManager dataManager;
    private Validator validator;
    private List<ConstraintViolation> constraintViolations;
    private Resource baseUri;
    
    public ImportImpl(Node n, EnhGraph g)
    {
        super(n, g);
    }
    
    @Override
    public Resource getFile()
    {
        return getPropertyResourceValue(APL.file);
    }
    
    @Override
    public Resource getContainer()
    {
        return getPropertyResourceValue(APL.action);
    }
    
    @Override
    public Import setDataManager(DataManager dataManager)
    {
        this.dataManager = dataManager;
        return this;
    }

    @Override
    public DataManager getDataManager()
    {
        return dataManager;
    }
    
    @Override
    public Import setValidator(Validator validator)
    {
        this.validator = validator;
        return this;
    }

    @Override
    public Validator getValidator()
    {
        return validator;
    }
    
    @Override
    public Import setBaseUri(Resource baseUri)
    {
        this.baseUri = baseUri;
        return this;
    }
    
    @Override
    public Resource getBaseUri()
    {
        return baseUri;
    }

    public void addConstraintViolations(List<ConstraintViolation> cvs)
    {
        for (ConstraintViolation cv : cvs)
            addProperty(APL.violation, constraintViolationToResource(getModel(), cv));
    }
    
    @Override
    public List<ConstraintViolation> getConstraintViolations()
    {
        return constraintViolations;
    }
    
    /**
     * Constructs the same RDF structure for spin:ConstraintViolation as SPIN API does.
     * 
     * @param model target model
     * @param cv constraint violation
     * @return violation as resource
     * @see org.spinrdf.constraints.SPINConstraints.addConstraintViolationsRDF(List<ConstraintViolation> cvs, Model result, boolean createSource)
     */
    protected Resource constraintViolationToResource(Model model, ConstraintViolation cv)
    {
        if (model == null) throw new IllegalArgumentException("Model cannot be null");
        if (cv == null) throw new IllegalArgumentException("ConstraintViolation cannot be null");
        
        Resource r = model.createResource(SPIN.ConstraintViolation);
        String message = cv.getMessage();
        
        r.addProperty(SPIN.violationLevel, cv.getLevel());
        if (message != null && message.length() > 0) r.addProperty(RDFS.label, message);
        if (cv.getRoot() != null) r.addProperty(SPIN.violationRoot, cv.getRoot());
        if (cv.getSource() != null) r.addProperty(SPIN.violationSource, cv.getSource());
        if (cv.getValue() != null) r.addProperty(SPIN.violationValue, cv.getValue());
        
        for (SimplePropertyPath path : cv.getPaths())
        {
            if (path instanceof ObjectPropertyPath) r.addProperty(SPIN.violationPath, path.getPredicate());
            else
            {
                Resource p = model.createResource(SP.ReversePath);
                p.addProperty(SP.path, path.getPredicate());
                r.addProperty(SPIN.violationPath, p);
            }
        }
        
        return r;
    }
    
}
