/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.tests.unit.deployment.structure.nonTransitiveResolution;

import static java.util.Arrays.asList;
import static org.jboss.weld.test.Utils.getReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;

import org.jboss.arquillian.container.weld.ee.embedded_1_1.mock.AbstractDeployment;
import org.jboss.arquillian.container.weld.ee.embedded_1_1.mock.BeanDeploymentArchiveImpl;
import org.jboss.arquillian.container.weld.ee.embedded_1_1.mock.FlatDeployment;
import org.jboss.arquillian.container.weld.ee.embedded_1_1.mock.MockServletServices;
import org.jboss.arquillian.container.weld.ee.embedded_1_1.mock.TestContainer;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.BeansXml;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.servlet.api.ServletServices;
import org.jboss.weld.test.Utils;
import org.junit.Assert;
import org.testng.annotations.Test;

public class TransitiveResolutionTest
{
   /*
    * description = "WELD-319"
    */
   @Test
   public void testBeansXmlIsolation()
   {
      BeanDeploymentArchiveImpl jar1 = new BeanDeploymentArchiveImpl("first-jar", new BeansXmlImpl(Arrays.asList(Alt.class.getName()), null, null, null), Alt.class);
      BeanDeploymentArchiveImpl jar2 = new BeanDeploymentArchiveImpl("second-jar", Alt.class);
      BeanDeploymentArchiveImpl war = new BeanDeploymentArchiveImpl("war");
      war.getBeanDeploymentArchives().add(jar1);
      war.getBeanDeploymentArchives().add(jar2);
      
      Deployment deployment = new FlatDeployment(war);
      
      TestContainer container = null;
      try
      {
         container = new TestContainer(deployment).startContainer().ensureRequestActive();
         BeanManagerImpl warBeanManager = (BeanManagerImpl) container.getBeanManager(war);
         BeanManagerImpl jar1BeanManager = (BeanManagerImpl) container.getBeanManager(jar1);         
         BeanManagerImpl jar2BeanManager = (BeanManagerImpl) container.getBeanManager(jar2);
         Assert.assertTrue(warBeanManager.getEnabled().getAlternativeClasses().isEmpty());
         Assert.assertFalse(jar1BeanManager.getEnabled().getAlternativeClasses().isEmpty());
         Assert.assertTrue(jar2BeanManager.getEnabled().getAlternativeClasses().isEmpty());
      }
      finally
      {
         if (container != null)
         {
            container.stopContainer();
         }
      }
   }
   
   /*
    * description = "WELD-319"
    */
   @Test
   public void testBeansXmlMultipleEnabling()
   {
      BeanDeploymentArchiveImpl jar1 = new BeanDeploymentArchiveImpl("first-jar", new BeansXmlImpl(Arrays.asList(Alt.class.getName()), null, null, null), Alt.class);
      BeanDeploymentArchiveImpl jar2 = new BeanDeploymentArchiveImpl("second-jar", new BeansXmlImpl(Arrays.asList(Alt.class.getName()), Collections.<String>emptyList(), null, null), Alt.class);
      BeanDeploymentArchiveImpl war = new BeanDeploymentArchiveImpl("war");
      war.getBeanDeploymentArchives().add(jar1);
      war.getBeanDeploymentArchives().add(jar2);
      
      Deployment deployment = new FlatDeployment(war);
      
      TestContainer container = null;
      try
      {
         container = new TestContainer(deployment).startContainer().ensureRequestActive();
         BeanManagerImpl warBeanManager = (BeanManagerImpl) container.getBeanManager(war);
         BeanManagerImpl jar1BeanManager = (BeanManagerImpl) container.getLifecycle().getBootstrap().getManager(jar1);         
         BeanManagerImpl jar2BeanManager = (BeanManagerImpl) container.getLifecycle().getBootstrap().getManager(jar2);
         Assert.assertTrue(warBeanManager.getEnabled().getAlternativeClasses().isEmpty());
         Assert.assertFalse(jar1BeanManager.getEnabled().getAlternativeClasses().isEmpty());
         Assert.assertFalse(jar2BeanManager.getEnabled().getAlternativeClasses().isEmpty());
      }
      finally
      {
         if (container != null)
         {
            container.stopContainer();
         }
      }
   }   

   /*
    * description = "WELD-236"
    */
   @Test
   public void testTypicalEarStructure()
   {

      // Create the BDA in which we will deploy Foo. This is equivalent to a ejb
      // jar
      final BeanDeploymentArchiveImpl ejbJar = new BeanDeploymentArchiveImpl("ejb-jar", Foo.class);

      // Create the BDA in which we will deploy Bar. This is equivalent to a war
      final BeanDeploymentArchiveImpl war = new BeanDeploymentArchiveImpl("war", Bar.class);

      // The war can access the ejb jar
      war.getBeanDeploymentArchives().add(ejbJar);

      // Create a deployment, any other classes are put into the ejb-jar (not
      // relevant to test)
      Deployment deployment = new AbstractDeployment(war, ejbJar)
      {

         public BeanDeploymentArchive loadBeanDeploymentArchive(Class<?> beanClass)
         {
            return ejbJar;
         }
                  
         @Override
         protected void configureServices()
         {
            super.configureServices();
            getServices().add(ServletServices.class, new MockServletServices(war));
         }

      };

      TestContainer container = new TestContainer(deployment);
      container.startContainer();
      container.ensureRequestActive();

      // Get the bean manager for war and ejb jar
      BeanManager warBeanManager = container.getBeanManager(war);
      BeanManager ejbJarBeanManager = container.getLifecycle().getBootstrap().getManager(ejbJar);

      Assert.assertEquals(1, warBeanManager.getBeans(Bar.class).size());
      Assert.assertEquals(1, warBeanManager.getBeans(Foo.class).size());
      Assert.assertEquals(1, ejbJarBeanManager.getBeans(Foo.class).size());
      Assert.assertEquals(0, ejbJarBeanManager.getBeans(Bar.class).size());
      Bar bar = Utils.getReference(warBeanManager, Bar.class);
      Assert.assertNotNull(bar.getFoo());
      Assert.assertNotNull(bar.getBeanManager());
      Assert.assertEquals(warBeanManager, bar.getBeanManager());
      Assert.assertEquals(ejbJarBeanManager, bar.getFoo().getBeanManager());
   }
   
   /*
    * WELD-507
    */
   @Test
   public void testInterceptorEnabledInWarButPackagedInEjbJar()
   {

      // Create the BDA in which we will deploy Foo. This is equivalent to a ejb
      // jar
      final BeanDeploymentArchiveImpl ejbJar = new BeanDeploymentArchiveImpl("ejb-jar", Basic.class, BasicInterceptor.class, Simple.class);

      // Create the BDA in which we will deploy Bar. This is equivalent to a war
      BeansXml beansXml = new BeansXmlImpl(null, null, null, asList(BasicInterceptor.class.getName()));
      final BeanDeploymentArchiveImpl war = new BeanDeploymentArchiveImpl("war", beansXml, Complex.class);

      // The war can access the ejb jar
      war.getBeanDeploymentArchives().add(ejbJar);

      // Create a deployment, any other classes are put into the ejb-jar (not
      // relevant to test)
      Deployment deployment = new AbstractDeployment(ejbJar, war)
      {

         public BeanDeploymentArchive loadBeanDeploymentArchive(Class<?> beanClass)
         {
            return ejbJar;
         }
                  
         @Override
         protected void configureServices()
         {
            super.configureServices();
            getServices().add(ServletServices.class, new MockServletServices(war));
         }

      };

      TestContainer container = new TestContainer(deployment);
      container.startContainer();
      container.ensureRequestActive();

      // Get the bean manager for war and ejb jar
      BeanManager warBeanManager = container.getBeanManager(war);
      BeanManager ejbJarBeanManager = container.getLifecycle().getBootstrap().getManager(ejbJar);

      
      BasicInterceptor.reset();
      Simple simple = getReference(ejbJarBeanManager, Simple.class);
      simple.ping("14");
      assertNull(BasicInterceptor.getTarget());
      
      BasicInterceptor.reset();
      Complex complex = getReference(warBeanManager, Complex.class);
      complex.ping("14");
      assertNotNull(BasicInterceptor.getTarget());
      assertTrue(BasicInterceptor.getTarget() instanceof Complex);
      assertEquals("14", ((Complex) BasicInterceptor.getTarget()).getId());
   }
   
   /*
    * WELD-507
    */
   @Test
   public void testDecoratorEnabledInWarButPackagedInEjbJar()
   {

      // Create the BDA in which we will deploy Foo. This is equivalent to a ejb
      // jar
      final BeanDeploymentArchiveImpl ejbJar = new BeanDeploymentArchiveImpl("ejb-jar", Blah.class, BlahDecorator.class, BlahImpl.class);

      // Create the BDA in which we will deploy Bar. This is equivalent to a war
      BeansXml beansXml = new BeansXmlImpl(null, null, asList(BlahDecorator.class.getName()), null);
      final BeanDeploymentArchiveImpl war = new BeanDeploymentArchiveImpl("war", beansXml, BlahImpl2.class);

      // The war can access the ejb jar
      war.getBeanDeploymentArchives().add(ejbJar);

      // Create a deployment, any other classes are put into the ejb-jar (not
      // relevant to test)
      Deployment deployment = new AbstractDeployment(war, ejbJar)
      {

         public BeanDeploymentArchive loadBeanDeploymentArchive(Class<?> beanClass)
         {
            return ejbJar;
         }
         
         @Override
         protected void configureServices()
         {
            super.configureServices();
            getServices().add(ServletServices.class, new MockServletServices(war));
         }

      };

      TestContainer container = new TestContainer(deployment);
      container.startContainer();
      container.ensureRequestActive();

      // Get the bean manager for war and ejb jar
      BeanManager warBeanManager = container.getBeanManager(war);
      BeanManager ejbJarBeanManager = container.getLifecycle().getBootstrap().getManager(ejbJar);

      
      BasicInterceptor.reset();
      Blah blah = getReference(ejbJarBeanManager, Blah.class);
      blah.ping(10);
      assertEquals(10, blah.getI());
      
      BasicInterceptor.reset();
      blah = getReference(warBeanManager, Blah.class, new AnnotationLiteral<Baz>() {});
      blah.ping(10);
      assertEquals(11, blah.getI());
   }

}
