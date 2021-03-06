package us.ihmc.quadrupedRobotics.controller.position;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import us.ihmc.quadrupedRobotics.QuadrupedMultiRobotTestInterface;
import us.ihmc.quadrupedRobotics.QuadrupedPositionTestYoVariables;
import us.ihmc.quadrupedRobotics.QuadrupedTestBehaviors;
import us.ihmc.quadrupedRobotics.QuadrupedTestFactory;
import us.ihmc.quadrupedRobotics.QuadrupedTestGoals;
import us.ihmc.quadrupedRobotics.controller.QuadrupedControlMode;
import us.ihmc.quadrupedRobotics.simulation.QuadrupedGroundContactModelType;
import us.ihmc.robotics.testing.YoVariableTestGoal;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.simulationconstructionset.util.simulationRunner.ControllerFailureException;
import us.ihmc.simulationconstructionset.util.simulationRunner.GoalOrientedTestConductor;
import us.ihmc.tools.MemoryTools;
import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;
import us.ihmc.tools.testing.TestPlanTarget;

public abstract class QuadrupedPositionCrawlFlatGroundWalkingTest implements QuadrupedMultiRobotTestInterface
{
   private GoalOrientedTestConductor conductor;
   private QuadrupedPositionTestYoVariables variables;
   
   @Before
   public void setup()
   {
      try
      {
         MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " before test.");

         QuadrupedTestFactory quadrupedTestFactory = createQuadrupedTestFactory();
         quadrupedTestFactory.setControlMode(QuadrupedControlMode.POSITION);
         quadrupedTestFactory.setGroundContactModelType(QuadrupedGroundContactModelType.FLAT);
         conductor = quadrupedTestFactory.createTestConductor();
         variables = new QuadrupedPositionTestYoVariables(conductor.getScs());
      }
      catch (IOException e)
      {
         throw new RuntimeException("Error loading simulation: " + e.getMessage());
      }
   }
   
   @After
   public void tearDown()
   {
      conductor = null;
      variables = null;
      
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }
   
   @DeployableTestMethod(estimatedDuration = 33.0, targets = {TestPlanTarget.Fast, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingForwardFast() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(0.25);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addSustainGoal(YoVariableTestGoal.doubleLessThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 25.0));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(variables.getRobotBodyX(), 1.5));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 33.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingForwardSlow() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(0.06);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addSustainGoal(YoVariableTestGoal.doubleLessThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 25.0));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(variables.getRobotBodyX(), 0.48));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 33.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingBackwardsFast() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(-0.25);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addSustainGoal(YoVariableTestGoal.doubleLessThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 25.0));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleLessThan(variables.getRobotBodyX(), -1.5));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 33.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingBackwardsSlow() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(-0.06);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addSustainGoal(YoVariableTestGoal.doubleLessThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 25.0));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleLessThan(variables.getRobotBodyX(), -0.48));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 70.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingInAForwardLeftCircle() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(0.25);
      variables.getYoPlanarVelocityInputZ().set(0.2);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 30.0));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 70.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingInAForwardRightCircle() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(-0.25);
      variables.getYoPlanarVelocityInputZ().set(-0.2);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 30.0));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 70.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingInABackwardLeftCircle() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(-0.25);
      variables.getYoPlanarVelocityInputZ().set(0.2);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 30.0));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
   
   @DeployableTestMethod(estimatedDuration = 70.0, targets = {TestPlanTarget.InDevelopment, TestPlanTarget.Video})
   @Test(timeout = 200000)
   public void testWalkingInABackwardRightCircle() throws SimulationExceededMaximumTimeException, ControllerFailureException, IOException
   {
      QuadrupedTestBehaviors.standUp(conductor, variables);
      
      variables.getYoPlanarVelocityInputX().set(-0.25);
      variables.getYoPlanarVelocityInputZ().set(-0.2);
      conductor.addSustainGoal(QuadrupedTestGoals.notFallen(variables));
      conductor.addTerminalGoal(YoVariableTestGoal.doubleGreaterThan(variables.getYoTime(), variables.getYoTime().getDoubleValue() + 30.0));
      conductor.simulate();
      
      conductor.concludeTesting();
   }
}
