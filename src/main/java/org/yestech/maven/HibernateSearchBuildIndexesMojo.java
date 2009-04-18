package org.yestech.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.commons.lang.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.classic.Session;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Goal which touches a timestamp file.
 *
 * @goal index
 * @phase test
 */
public class HibernateSearchBuildIndexesMojo extends AbstractMojo
{

    /**
     * @parameter
     * @required
     */
    private String url;

    /**
     * @parameter
     * @required
     */
    private String driver;

    /**
     * @parameter
     * @required
     */
    private String username;

    /**
     * @parameter
     * @required
     */
    private String password;

    /**
     * @parameter
     */
    private String dialect;

    /**
     * @parameter
     */
    private String indexDir;


    /**
     * @parameter
     */
    private String directoryProvider;

    /**
     * @parameter
     * @required
     */
    private File config;

    /**
     * <i>Maven Internal</i>: Project to interact with.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @noinspection UnusedDeclaration
     */
    private MavenProject project;


    @SuppressWarnings({"unchecked"})
    public void execute() throws MojoExecutionException
    {

        Thread thread = Thread.currentThread();
        ClassLoader oldClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(getClassLoader());


        FullTextSession fullTextSession = null;
        Connection con = null;
        try
        {

            Class.forName(driver);
            con = java.sql.DriverManager.getConnection(url, username, password);

            Configuration configuration = new AnnotationConfiguration();
            configuration = configuration.configure(config);
            if (StringUtils.isNotBlank(dialect)) {
                configuration.setProperty("hibernate.dialect", dialect);
            }
            if (StringUtils.isNotBlank(indexDir)) {
                configuration.setProperty("hibernate.index.dir", indexDir);
            }
            if (StringUtils.isNotBlank(directoryProvider)) {
                configuration.setProperty("hibernate.search.default.directory_provider", directoryProvider);
            }

            SessionFactory sessionFactory = configuration.buildSessionFactory();
            Session session = sessionFactory.openSession(con);
            fullTextSession = Search.getFullTextSession(session);

            Transaction tx = fullTextSession.beginTransaction();

            Map<String, ClassMetadata> metadata = sessionFactory.getAllClassMetadata();

            for (String entityName : metadata.keySet())
            {
                String hql = "from " + entityName;
                List<?> list = session.createQuery(hql).list();
                for (Object o : list)
                {
                    fullTextSession.index(o);
                }
            }

            tx.commit();
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Build ", e);

        }
        finally
        {
            if (fullTextSession != null)
            {
                fullTextSession.flushToIndexes();
                fullTextSession.flush();
                fullTextSession.close();
            }
            if (con != null)
            {
                try
                {
                    con.close();
                }
                catch (SQLException e)
                {
                    getLog().error(e);
                }
            }
            thread.setContextClassLoader(oldClassLoader);
        }

    }

    /**
     * Returns the an isolated classloader.
     *
     * @return ClassLoader
     * @noinspection unchecked
     */
    private ClassLoader getClassLoader()
    {
        try
        {
            List classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL urls[] = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i)
            {
                urls[i] = new File((String) classpathElements.get(i)).toURL();
            }
            return new URLClassLoader(urls, this.getClass().getClassLoader());
        }
        catch (Exception e)
        {
            getLog().debug("Couldn't get the classloader.");
            return this.getClass().getClassLoader();
        }
    }

}
