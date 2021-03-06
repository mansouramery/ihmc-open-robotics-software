package us.ihmc.llaQuadruped;

import java.io.IOException;

import us.ihmc.SdfLoader.OutputWriter;
import us.ihmc.SdfLoader.SDFFullQuadrupedRobotModel;
import us.ihmc.SdfLoader.SDFPerfectSimulatedOutputWriter;
import us.ihmc.SdfLoader.SDFRobot;
import us.ihmc.commonWalkingControlModules.pushRecovery.PushRobotController;
import us.ihmc.communication.net.NetClassList;
import us.ihmc.graphics3DAdapter.GroundProfile3D;
import us.ihmc.llaQuadruped.simulation.LLAQuadrupedGroundContactParameters;
import us.ihmc.quadrupedRobotics.QuadrupedTestFactory;
import us.ihmc.quadrupedRobotics.controller.QuadrupedControlMode;
import us.ihmc.quadrupedRobotics.controller.position.states.QuadrupedPositionBasedCrawlControllerParameters;
import us.ihmc.quadrupedRobotics.estimator.referenceFrames.QuadrupedReferenceFrames;
import us.ihmc.quadrupedRobotics.estimator.stateEstimator.QuadrupedSensorInformation;
import us.ihmc.quadrupedRobotics.factories.QuadrupedSimulationFactory;
import us.ihmc.quadrupedRobotics.model.QuadrupedModelFactory;
import us.ihmc.quadrupedRobotics.model.QuadrupedPhysicalProperties;
import us.ihmc.quadrupedRobotics.model.QuadrupedSimulationInitialPositionParameters;
import us.ihmc.quadrupedRobotics.params.ParameterRegistry;
import us.ihmc.quadrupedRobotics.simulation.QuadrupedGroundContactModelType;
import us.ihmc.quadrupedRobotics.simulation.QuadrupedGroundContactParameters;
import us.ihmc.sensorProcessing.sensorProcessors.SensorTimestampHolder;
import us.ihmc.sensorProcessing.stateEstimation.StateEstimatorParameters;
import us.ihmc.simulationconstructionset.bambooTools.SimulationTestingParameters;
import us.ihmc.simulationconstructionset.util.simulationRunner.GoalOrientedTestConductor;
import us.ihmc.tools.factories.FactoryTools;
import us.ihmc.tools.factories.OptionalFactoryField;
import us.ihmc.tools.factories.RequiredFactoryField;

public class LLAQuadrupedTestFactory implements QuadrupedTestFactory
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();
   private static final double SIMULATION_DT = 0.00006;
   private static final double SIMULATION_GRAVITY = -9.81;
   private static final int RECORD_FREQUENCY = (int) (0.01 / SIMULATION_DT);
   private static final boolean USE_STATE_ESTIMATOR = false;
   private static final boolean SHOW_PLOTTER = true;
   private static final boolean USE_TRACK_AND_DOLLY = false;
   private static final boolean USE_NETWORKING = false;
   
   private final RequiredFactoryField<QuadrupedControlMode> controlMode = new RequiredFactoryField<>("controlMode");
   
   private final OptionalFactoryField<Boolean> useStateEstimator = new OptionalFactoryField<>("useStateEstimator");
   private final OptionalFactoryField<QuadrupedGroundContactModelType> groundContactModelType = new OptionalFactoryField<>("groundContactModelType");
   private final OptionalFactoryField<GroundProfile3D> providedGroundProfile3D = new OptionalFactoryField<>("providedGroundProfile3D");
   private final OptionalFactoryField<String> usePushRobotController = new OptionalFactoryField<>("usePushRobotController");
   
   @Override
   public GoalOrientedTestConductor createTestConductor() throws IOException
   {
      FactoryTools.checkAllRequiredFactoryFieldsAreSet(this);
      
      QuadrupedModelFactory modelFactory = new LLAQuadrupedModelFactory();
      QuadrupedPhysicalProperties physicalProperties = new LLAQuadrupedPhysicalProperties();
      NetClassList netClassList = new LLAQuadrupedNetClassList();
      QuadrupedSimulationInitialPositionParameters initialPositionParameters = new LLAQuadrupedSimulationInitialPositionParameters();
      QuadrupedGroundContactParameters groundContactParameters = new LLAQuadrupedGroundContactParameters();
      QuadrupedSensorInformation sensorInformation = new LLAQuadrupedSensorInformation();
      ParameterRegistry.getInstance().loadFromResources("parameters/simulation.param");
      StateEstimatorParameters stateEstimatorParameters = new LLAQuadrupedStateEstimatorParameters();
      QuadrupedPositionBasedCrawlControllerParameters positionBasedCrawlControllerParameters = new LLAQuadrupedPositionBasedCrawlControllerParameters();
      
      SDFFullQuadrupedRobotModel fullRobotModel = modelFactory.createFullRobotModel();
      SDFRobot sdfRobot = modelFactory.createSdfRobot();
      
      SensorTimestampHolder timestampProvider = new LLAQuadrupedTimestampProvider(sdfRobot);
      
      QuadrupedReferenceFrames referenceFrames = new QuadrupedReferenceFrames(fullRobotModel, physicalProperties);
      OutputWriter outputWriter = new SDFPerfectSimulatedOutputWriter(sdfRobot, fullRobotModel);
      
      if (usePushRobotController.hasBeenSet())
      {
         new PushRobotController(sdfRobot, usePushRobotController.get());
      }
      
      QuadrupedSimulationFactory simulationFactory = new QuadrupedSimulationFactory();
      simulationFactory.setControlDT(SIMULATION_DT);
      simulationFactory.setGravity(SIMULATION_GRAVITY);
      simulationFactory.setRecordFrequency(RECORD_FREQUENCY);
      simulationFactory.setGroundContactParameters(groundContactParameters);
      simulationFactory.setModelFactory(modelFactory);
      simulationFactory.setSDFRobot(sdfRobot);
      simulationFactory.setSCSParameters(simulationTestingParameters);
      simulationFactory.setOutputWriter(outputWriter);
      simulationFactory.setShowPlotter(SHOW_PLOTTER);
      simulationFactory.setUseTrackAndDolly(USE_TRACK_AND_DOLLY);
      simulationFactory.setInitialPositionParameters(initialPositionParameters);
      simulationFactory.setFullRobotModel(fullRobotModel);
      simulationFactory.setPhysicalProperties(physicalProperties);
      simulationFactory.setUseNetworking(USE_NETWORKING);
      simulationFactory.setTimestampHolder(timestampProvider);
      if (useStateEstimator.hasBeenSet())
      {
         simulationFactory.setUseStateEstimator(useStateEstimator.get());
      }
      else
      {
         simulationFactory.setUseStateEstimator(USE_STATE_ESTIMATOR);
      }
      simulationFactory.setStateEstimatorParameters(stateEstimatorParameters);
      simulationFactory.setSensorInformation(sensorInformation);
      simulationFactory.setReferenceFrames(referenceFrames);
      simulationFactory.setNetClassList(netClassList);
      simulationFactory.setControlMode(controlMode.get());
      if (groundContactModelType.hasBeenSet())
      {
         simulationFactory.setGroundContactModelType(groundContactModelType.get());
      }
      if (providedGroundProfile3D.hasBeenSet())
      {
         simulationFactory.setGroundProfile3D(providedGroundProfile3D.get());
      }
      simulationFactory.setPositionBasedCrawlControllerParameters(positionBasedCrawlControllerParameters);
      return new GoalOrientedTestConductor(simulationFactory.createSimulation(), simulationTestingParameters);
   }
   
   @Override
   public void setControlMode(QuadrupedControlMode controlMode)
   {
      this.controlMode.set(controlMode);
   }
   
   @Override
   public void setGroundContactModelType(QuadrupedGroundContactModelType groundContactModelType)
   {
      this.groundContactModelType.set(groundContactModelType);
   }
   
   @Override
   public void setUseStateEstimator(boolean useStateEstimator)
   {
      this.useStateEstimator.set(useStateEstimator);
   }
   
   @Override
   public void setGroundProfile3D(GroundProfile3D groundProfile3D)
   {
      providedGroundProfile3D.set(groundProfile3D);
   }
   
   @Override
   public void setUsePushRobotController(String jointNameToPushOn)
   {
      this.usePushRobotController.set(jointNameToPushOn);
   }
}
