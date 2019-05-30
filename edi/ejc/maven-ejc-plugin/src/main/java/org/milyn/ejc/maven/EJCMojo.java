/*
	Milyn - Copyright (C) 2006 - 2010

	This library is free software; you can redistribute it and/or
	modify it under the terms of the GNU Lesser General Public
	License (version 2.1) as published by the Free Software
	Foundation.

	This library is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

	See the GNU Lesser General Public License for more details:
	http://www.gnu.org/licenses/lgpl.txt
*/
package org.milyn.ejc.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.milyn.ejc.EJCExecutor;
import org.milyn.ejc.EJCException;
import org.milyn.edisax.util.IllegalNameException;
import org.xml.sax.SAXException;

import java.util.List;
import java.io.File;
import java.io.IOException;

/**
 * EJC Mojo.
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
@Execute( goal = "generate",
phase = LifecyclePhase.GENERATE_SOURCES,
lifecycle = "generate-sources" )
@Mojo( name = "generate",
requiresDependencyResolution = ResolutionScope.COMPILE)
public class EJCMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "target/ejc", required = false)
    private File destDir;

    @Parameter(required = false)
    private String messages;
    
    @Parameter(required = true)
    private List<EdiMapping> ediMappings;

    public void execute() throws MojoExecutionException {
    	getLog().info("Execution EJC Plugin");
    	
        EJCExecutor ejc = new EJCExecutor();

        try {
            if(destDir.exists()) {
                destDir.delete();
            }
            
            ejc.setMessages(messages);
            ejc.setDestDir(destDir);
            
            for(EdiMapping ediMapping : ediMappings) {
	            ejc.setEdiMappingModel(ediMapping.modelSourceFile);
	            ejc.setPackageName(ediMapping.packageName);
	            ejc.execute();
            }

            project.addCompileSourceRoot(destDir.getPath());

            Resource resource = new Resource();
            resource.setDirectory(destDir.getPath());
            resource.addInclude("**/*.xml");
            resource.addInclude("**/*.lst");
            project.addResource(resource);
        } catch (EJCException e) {
            throw new MojoExecutionException("Error Executing EJC Maven Plugin.  See chained cause.", e);
        } catch (SAXException e) {
            throw new MojoExecutionException("Error Executing EJC Maven Plugin.  See chained cause.", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error Executing EJC Maven Plugin.  See chained cause.", e);
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException("Error Executing EJC Maven Plugin.  See chained cause.", e);
        } catch (IllegalNameException e) {
            throw new MojoExecutionException("Error Executing EJC Maven Plugin.  See chained cause.", e);
        }
    }
}
