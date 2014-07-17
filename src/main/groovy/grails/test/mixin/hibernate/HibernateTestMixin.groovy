/* Copyright (C) 2014 SpringSource
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
package grails.test.mixin.hibernate

import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.test.mixin.support.GrailsUnitTestMixin
import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.InstanceFactoryBean
import org.codehaus.groovy.grails.orm.hibernate.support.HibernatePersistenceContextInterceptor
import org.grails.datastore.gorm.GormEnhancer
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.hibernate4.SessionHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

import javax.sql.DataSource

/**
 * A Mixin that sets up a Hibernate domain model
 *
 * @author Graeme Rocher
 * @since 3.0.4
 */
class HibernateTestMixin extends GrailsUnitTestMixin{

    /**
     * The current transaction manager
     */
    static PlatformTransactionManager transactionManager

    /**
     * The current session factory. Will be closed with by the ApplicationContext
     */
    static SessionFactory sessionFactory

    /**
     * Used to setup session handling
     */
    HibernatePersistenceContextInterceptor hibernateInterceptor

    /**
     * Current hibernate session
     */
    Session session

    /**
     * Sets up a GORM for Hibernate domain for the given domain classes
     *
     * @param persistentClasses
     */
    @CompileStatic
    static SessionFactory hibernateDomain(Collection<Class> persistentClasses) {
        def initializer = new HibernateDatastoreSpringInitializer(persistentClasses)
        configureDefaultDataSource()
        completeConfiguration(persistentClasses,initializer)
    }

    @Before
    void connectionSession() {
        if(sessionFactory) {
            hibernateInterceptor = new HibernatePersistenceContextInterceptor(sessionFactory: sessionFactory)
            hibernateInterceptor.init()
            SessionHolder holder = TransactionSynchronizationManager.getResource(sessionFactory)
            session = holder.getSession()
        }
    }

    @After
    void destroySession() {
        if(hibernateInterceptor) {
            hibernateInterceptor.destroy()
            session = null
            hibernateInterceptor = null
        }
    }

    @AfterClass
    static void cleanupHibernate() {
        transactionManager = null
        sessionFactory = null
    }

    protected static void configureDefaultDataSource() {
        defineBeans {
            dataSource(DriverManagerDataSource) {
                url = "jdbc:h2:mem:grailsDB;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
                driverClassName = "org.h2.Driver"
                username = "sa"
                password = ""
            }
        }
    }

    /**
     * Sets up a GORM for Hibernate domain for the given Mongo instance and domain classes
     *
     * @param persistentClasses
     */
    static SessionFactory hibernateDomain(DataSource dataSource, Collection<Class> persistentClasses) {
        def initializer = new HibernateDatastoreSpringInitializer(persistentClasses)
        defineBeans {
            delegate.dataSource(InstanceFactoryBean, dataSource)
        }
        completeConfiguration(persistentClasses,initializer)
    }

    /**
     * Sets up a GORM for MongoDB domain for the given configuration and domain classes
     *
     * @param persistentClasses
     */
    @CompileStatic
    static SessionFactory hibernateDomain(Map config, Collection<Class> persistentClasses) {
        def initializer = new HibernateDatastoreSpringInitializer(persistentClasses)
        def props = new Properties()
        props.putAll(config)
        initializer.setConfiguration(props)
        configureDefaultDataSource()
        completeConfiguration(persistentClasses,initializer)
    }

    @CompileStatic
    protected static SessionFactory completeConfiguration(Collection<Class> persistentClasses, HibernateDatastoreSpringInitializer initializer) {
        def application = getGrailsApplication()
        for(cls in persistentClasses) {
            application.addArtefact(DomainClassArtefactHandler.TYPE, cls)
        }

        def context = getApplicationContext()
        initializer.configureForBeanDefinitionRegistry(context)
        context.getBeansOfType(GormEnhancer)
        transactionManager = context.getBean(PlatformTransactionManager)
        sessionFactory = context.getBean(SessionFactory)
        return sessionFactory
    }
}
