package us.ihmc.simulationconstructionset.util.dataProcessors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import us.ihmc.tools.testing.TestPlanTarget;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestClass;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;

@DeployableTestClass(targets = {TestPlanTarget.Fast})
public class ValueDataCheckerParametersTest
{
   private double EPSILON = 1e-10;
   
   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000)
      public void testGetDefensiveCopy()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();
      valueDataCheckerParametersOriginal.setErrorThresholdOnDerivativeComparison(Math.random());
      valueDataCheckerParametersOriginal.setMaximumDerivative(Math.random());
      valueDataCheckerParametersOriginal.setMaximumSecondDerivative(Math.random());
      valueDataCheckerParametersOriginal.setMaximumValue(Math.random());
      valueDataCheckerParametersOriginal.setMinimumValue(valueDataCheckerParametersOriginal.getMaximumValue() - 1.0);
      
      ValueDataCheckerParameters valueDataCheckerParametersCopy = valueDataCheckerParametersOriginal.getDefensiveCopy();
      
      assertEquals(valueDataCheckerParametersOriginal.getErrorThresholdOnDerivativeComparison(), valueDataCheckerParametersCopy.getErrorThresholdOnDerivativeComparison(), EPSILON);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumDerivative(), valueDataCheckerParametersCopy.getMaximumDerivative(), EPSILON);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumSecondDerivative(), valueDataCheckerParametersCopy.getMaximumSecondDerivative(), EPSILON);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumValue(), valueDataCheckerParametersCopy.getMaximumValue(), EPSILON);
      assertEquals(valueDataCheckerParametersOriginal.getMinimumValue(), valueDataCheckerParametersCopy.getMinimumValue(), EPSILON);
   }

   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000)
      public void testErrorThresholdOnDerivativeComparison()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = Math.random();
      valueDataCheckerParametersOriginal.setErrorThresholdOnDerivativeComparison(value);
      assertEquals(valueDataCheckerParametersOriginal.getErrorThresholdOnDerivativeComparison(), value, EPSILON);
      
      valueDataCheckerParametersOriginal.setErrorThresholdOnDerivativeComparison(-value);
      assertEquals(valueDataCheckerParametersOriginal.getErrorThresholdOnDerivativeComparison(), value, EPSILON);
   }


   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000)
      public void testMaximumDerivative()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = Math.random();
      valueDataCheckerParametersOriginal.setMaximumDerivative(value);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumDerivative(), value, EPSILON);
      
      valueDataCheckerParametersOriginal.setMaximumDerivative(-value);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumDerivative(), value, EPSILON);
   }


   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testMaximumSecondDerivative()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = Math.random();
      valueDataCheckerParametersOriginal.setMaximumSecondDerivative(value);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumSecondDerivative(), value, EPSILON);
      
      valueDataCheckerParametersOriginal.setMaximumSecondDerivative(-value);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumSecondDerivative(), value, EPSILON);
   }

   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000)
      public void testMaximumValue()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = Math.random();
      valueDataCheckerParametersOriginal.setMaximumValue(value);
      assertEquals(valueDataCheckerParametersOriginal.getMaximumValue(), value, EPSILON);
      
      valueDataCheckerParametersOriginal.setMaximumValue(-value);
      assertFalse(valueDataCheckerParametersOriginal.getMaximumValue() == value);
   }


   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   
   public void testMinimumValue()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = Math.random();
      valueDataCheckerParametersOriginal.setMinimumValue(value);
      assertEquals(valueDataCheckerParametersOriginal.getMinimumValue(), value, EPSILON);
      
      valueDataCheckerParametersOriginal.setMinimumValue(-value);
      assertFalse(valueDataCheckerParametersOriginal.getMinimumValue() == value);
   }

   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000, expected=RuntimeException.class)
   public void testSetMinGreaterThanMax()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = 10.0;
      valueDataCheckerParametersOriginal.setMaximumValue(value);
      valueDataCheckerParametersOriginal.setMinimumValue(value + 1.0);
   }
   
   @DeployableTestMethod(estimatedDuration = 0.0)
   @Test(timeout = 30000, expected=RuntimeException.class)
   public void testSetMaxLessThanMin()
   {
      ValueDataCheckerParameters valueDataCheckerParametersOriginal = new ValueDataCheckerParameters();

      double value = 10.0;
      valueDataCheckerParametersOriginal.setMinimumValue(value);
      valueDataCheckerParametersOriginal.setMaximumValue(value - 10.0);
   }

}
