/*
 *   Copyright 2014 Florida Institute for Human and Machine Cognition (IHMC)
 *    
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *    
 *    Written by Jesper Smith with assistance from IHMC team members
 */
package us.ihmc.graphics3DAdapter.stlLoader;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import us.ihmc.graphics3DAdapter.stlLoader.NormalCalculator;
import us.ihmc.graphics3DAdapter.stlLoader.STLReader;
import us.ihmc.graphics3DAdapter.stlLoader.STLReaderFactory;
import us.ihmc.graphics3DAdapter.stlLoader.Triangle;
import us.ihmc.tools.testing.TestPlanTarget;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestClass;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;

/**
 * Test the normal calculation routine based on the normals in the the teapotBinary.STL model.
 * 
 * @author Jesper Smith
 *
 */
@DeployableTestClass(targets={TestPlanTarget.UI})
public class NormalCalculatorTest
{

	@DeployableTestMethod(estimatedDuration = 0.0)
	@Test(timeout = 30000)
   public void testNormalsBasedOnTeapot() throws IOException
   {
      InputStream stream = getClass().getClassLoader().getResourceAsStream("teapotBinary.STL");
      STLReader reader = STLReaderFactory.create(stream);


      for(Triangle triangle : reader.getTriangles())
      {
         float[] normal = triangle.getNormal();
         float[] calculatedNormal = new float[3];
         NormalCalculator.calculateNormal(calculatedNormal, triangle.getVertices());
         
         assertTrue(NormalCalculator.compareNormal(normal, calculatedNormal, 1e-2f));
      }
   }
}
