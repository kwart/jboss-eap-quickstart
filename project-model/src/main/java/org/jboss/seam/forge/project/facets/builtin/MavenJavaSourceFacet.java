/*
 * JBoss, by Red Hat.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.seam.forge.project.facets.builtin;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.jboss.seam.forge.parser.JavaParser;
import org.jboss.seam.forge.parser.java.JavaClass;
import org.jboss.seam.forge.parser.java.util.Formatter;
import org.jboss.seam.forge.project.Facet;
import org.jboss.seam.forge.project.Project;
import org.jboss.seam.forge.project.ProjectModelException;
import org.jboss.seam.forge.project.Resource;
import org.jboss.seam.forge.project.constraints.RequiresFacets;
import org.jboss.seam.forge.project.facets.JavaSourceFacet;
import org.jboss.seam.forge.project.facets.MavenCoreFacet;
import org.jboss.seam.forge.project.resources.FileResource;
import org.jboss.seam.forge.project.resources.builtin.DirectoryResource;
import org.jboss.seam.forge.project.resources.builtin.JavaResource;
import org.jboss.seam.forge.project.util.Packages;

import javax.enterprise.context.Dependent;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
@Dependent
@Named("forge.maven.JavaSourceFacet")
@RequiresFacets({MavenCoreFacet.class})
public class MavenJavaSourceFacet implements JavaSourceFacet, Facet
{
   private Project project;

   @Override
   public List<DirectoryResource> getSourceFolders()
   {
      List<DirectoryResource> result = new ArrayList<DirectoryResource>();
      result.add(getSourceFolder());
      result.add(getTestSourceFolder());
      return result;
   }

   @Override
   public DirectoryResource getSourceFolder()
   {
      DirectoryResource projectRoot = project.getProjectRoot();
      return (DirectoryResource) projectRoot.getChild("src" + File.separator + "main" + File.separator + "java");
   }

   @Override
   public DirectoryResource getTestSourceFolder()
   {
      DirectoryResource projectRoot = project.getProjectRoot();
      return (DirectoryResource) projectRoot.getChild("src" + File.separator + "test" + File.separator + "java");
   }

   @Override
   public Project getProject()
   {
      return project;
   }

   @Override
   public void setProject(final Project project)
   {
      this.project = project;
   }

   @Override
   public boolean isInstalled()
   {
      MavenCoreFacet mavenFacet = project.getFacet(MavenCoreFacet.class);
      return getSourceFolder().exists() && (mavenFacet != null) && mavenFacet.isInstalled();
   }

   @Override
   public Facet install()
   {
      if (!this.isInstalled())
      {
         for (DirectoryResource folder : this.getSourceFolders())
         {
            folder.mkdirs();
         }

         // FIXME WOW this needs to be simplified somehow...
         MavenCoreFacet maven = project.getFacet(MavenCoreFacet.class);
         Model pom = maven.getPOM();
         Build build = pom.getBuild();
         if (build == null)
         {
            build = new Build();
         }
         List<Plugin> plugins = build.getPlugins();
         Plugin javaSourcePlugin = null;
         for (Plugin plugin : plugins)
         {
            if ("org.apache.maven.plugins".equals(plugin.getGroupId())
                  && "maven-compiler-plugin".equals(plugin.getArtifactId()))
            {
               javaSourcePlugin = plugin;
            }
         }

         if (javaSourcePlugin == null)
         {
            javaSourcePlugin = new Plugin();
            javaSourcePlugin.setGroupId("org.apache.maven.plugins");
            javaSourcePlugin.setArtifactId("maven-compiler-plugin");

            try
            {
               Xpp3Dom dom = Xpp3DomBuilder.build(
                     new ByteArrayInputStream(
                           "<configuration><source>1.6</source><target>1.6</target></configuration>".getBytes()),
                     "UTF-8");

               javaSourcePlugin.setConfiguration(dom);
            }
            catch (Exception e)
            {
               throw new ProjectModelException(e);
            }
         }

         build.addPlugin(javaSourcePlugin);
         pom.setBuild(build);
         maven.setPOM(pom);

      }
      project.registerFacet(this);
      return this;
   }

   @Override
   public JavaResource getSourceFile(final String relativePath)
   {
      return (JavaResource) getSourceFolder().getChild(relativePath);
   }

   @Override
   public JavaResource getTestSourceFile(final String relativePath)
   {
      return (JavaResource) getTestSourceFolder().getChild(relativePath);
   }

   @Override
   public JavaClass getJavaClass(final String relativePath) throws FileNotFoundException
   {
      JavaResource target = getSourceFile(relativePath);

      if (!target.exists())
      {
         JavaResource secondary = getSourceFile(Packages.toFileSyntax(relativePath) + ".java");
         if (secondary.exists())
         {
            target = secondary;
         }
      }

      return JavaParser.parse(target.getResourceInputStream());
   }

   @Override
   public JavaClass getTestJavaClass(final String relativePath) throws FileNotFoundException
   {
      JavaResource target = getTestSourceFile(relativePath);

      if (!target.exists())
      {
         JavaResource secondary = getTestSourceFile(Packages.toFileSyntax(relativePath) + ".java");
         if (secondary.exists())
         {
            target = secondary;
         }
      }

      return JavaParser.parse(target.getResourceInputStream());
   }

   @Override
   public String getBasePackage()
   {
      return project.getFacet(MavenCoreFacet.class).getMavenProject().getGroupId();
   }

   @Override
   public DirectoryResource getBasePackageResource()
   {
      return (DirectoryResource) getSourceFolder().getChild(getBasePackage());
   }

   @Override
   public JavaClass getJavaClass(final JavaClass javaClass) throws FileNotFoundException
   {
      String pkg = javaClass.getPackage() + "." + javaClass.getName();
      String path = Packages.toFileSyntax(pkg) + ".java";
      return getSourceFile(path).getJavaClass();
   }

   @Override
   public JavaClass getTestJavaClass(final JavaClass javaClass) throws FileNotFoundException
   {
      String pkg = javaClass.getPackage() + "." + javaClass.getName();
      String path = Packages.toFileSyntax(pkg) + ".java";
      return getTestSourceFile(path).getJavaClass();
   }

   private JavaResource saveJavaFile(final DirectoryResource sourceFolder, final JavaClass clazz)
   {
      FileResource path = (FileResource) sourceFolder.getChild(Packages.toFileSyntax(clazz.getPackage()));
      JavaResource file = (JavaResource) path.getChild(clazz.getName() + ".java");

      file.setContents(Formatter.format(clazz));
      // TODO event.fire(Created new Java file);
      return file;
   }

   @Override
   public JavaResource saveJavaClass(final JavaClass clazz)
   {
      return saveJavaFile(getSourceFolder(), clazz);
   }

   @Override
   public JavaResource saveTestJavaClass(final JavaClass clazz)
   {
      return saveJavaFile(getTestSourceFolder(), clazz);
   }
}
