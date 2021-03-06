package us.ihmc.quadrupedRobotics.factories;

import java.io.IOException;
import java.net.BindException;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;

import us.ihmc.SdfLoader.OutputWriter;
import us.ihmc.SdfLoader.SDFFullQuadrupedRobotModel;
import us.ihmc.SdfLoader.SDFRobot;
import us.ihmc.SdfLoader.partNames.QuadrupedJointName;
import us.ihmc.commonWalkingControlModules.sensors.footSwitch.FootSwitchInterface;
import us.ihmc.communication.net.NetClassList;
import us.ihmc.communication.packetCommunicator.PacketCommunicator;
import us.ihmc.communication.streamingData.GlobalDataProducer;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.graphics3DAdapter.GroundProfile3D;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.quadrupedRobotics.communication.QuadrupedGlobalDataProducer;
import us.ihmc.quadrupedRobotics.controller.QuadrupedControlMode;
import us.ihmc.quadrupedRobotics.controller.QuadrupedControllerManager;
import us.ihmc.quadrupedRobotics.controller.QuadrupedSimulationController;
import us.ihmc.quadrupedRobotics.controller.force.QuadrupedForceControllerManager;
import us.ihmc.quadrupedRobotics.controller.forceDevelopment.QuadrupedForceDevelopmentControllerManager;
import us.ihmc.quadrupedRobotics.controller.position.QuadrupedPositionControllerManager;
import us.ihmc.quadrupedRobotics.controller.position.states.QuadrupedPositionBasedCrawlControllerParameters;
import us.ihmc.quadrupedRobotics.controller.positionDevelopment.QuadrupedPositionDevelopmentControllerManager;
import us.ihmc.quadrupedRobotics.estimator.referenceFrames.QuadrupedReferenceFrames;
import us.ihmc.quadrupedRobotics.estimator.sensorProcessing.simulatedSensors.SDFQuadrupedPerfectSimulatedSensor;
import us.ihmc.quadrupedRobotics.estimator.stateEstimator.QuadrupedFootContactableBodiesFactory;
import us.ihmc.quadrupedRobotics.estimator.stateEstimator.QuadrupedFootSwitchFactory;
import us.ihmc.quadrupedRobotics.estimator.stateEstimator.QuadrupedSensorInformation;
import us.ihmc.quadrupedRobotics.estimator.stateEstimator.QuadrupedStateEstimatorFactory;
import us.ihmc.quadrupedRobotics.mechanics.inverseKinematics.QuadrupedInverseKinematicsCalculators;
import us.ihmc.quadrupedRobotics.mechanics.inverseKinematics.QuadrupedLegInverseKinematicsCalculator;
import us.ihmc.quadrupedRobotics.model.QuadrupedModelFactory;
import us.ihmc.quadrupedRobotics.model.QuadrupedPhysicalProperties;
import us.ihmc.quadrupedRobotics.model.QuadrupedRuntimeEnvironment;
import us.ihmc.quadrupedRobotics.model.QuadrupedSimulationInitialPositionParameters;
import us.ihmc.quadrupedRobotics.simulation.QuadrupedGroundContactModelType;
import us.ihmc.quadrupedRobotics.simulation.QuadrupedGroundContactParameters;
import us.ihmc.robotics.robotSide.QuadrantDependentList;
import us.ihmc.robotics.screwTheory.SixDoFJoint;
import us.ihmc.robotics.sensors.ContactSensorHolder;
import us.ihmc.robotics.sensors.ForceSensorDefinition;
import us.ihmc.robotics.sensors.IMUDefinition;
import us.ihmc.sensorProcessing.communication.producers.DRCPoseCommunicator;
import us.ihmc.sensorProcessing.model.DesiredJointDataHolder;
import us.ihmc.sensorProcessing.sensorData.JointConfigurationGatherer;
import us.ihmc.sensorProcessing.sensorProcessors.SensorTimestampHolder;
import us.ihmc.sensorProcessing.sensors.RawJointSensorDataHolderMap;
import us.ihmc.sensorProcessing.simulatedSensors.SensorReader;
import us.ihmc.sensorProcessing.simulatedSensors.SimulatedSensorHolderAndReaderFromRobotFactory;
import us.ihmc.sensorProcessing.stateEstimation.FootSwitchType;
import us.ihmc.sensorProcessing.stateEstimation.StateEstimatorParameters;
import us.ihmc.simulationconstructionset.OneDegreeOfFreedomJoint;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.SimulationConstructionSetParameters;
import us.ihmc.simulationconstructionset.UnreasonableAccelerationException;
import us.ihmc.simulationconstructionset.gui.tools.VisualizerUtils;
import us.ihmc.simulationconstructionset.robotController.RobotController;
import us.ihmc.simulationconstructionset.util.LinearGroundContactModel;
import us.ihmc.simulationconstructionset.util.ground.AlternatingSlopesGroundProfile;
import us.ihmc.simulationconstructionset.util.ground.FlatGroundProfile;
import us.ihmc.simulationconstructionset.util.ground.RollingGroundProfile;
import us.ihmc.simulationconstructionset.util.ground.RotatablePlaneTerrainProfile;
import us.ihmc.simulationconstructionset.util.ground.VaryingStairGroundProfile;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.stateEstimation.humanoid.kinematicsBasedStateEstimation.DRCKinematicsBasedStateEstimator;
import us.ihmc.tools.factories.FactoryTools;
import us.ihmc.tools.factories.OptionalFactoryField;
import us.ihmc.tools.factories.RequiredFactoryField;
import us.ihmc.tools.io.printing.PrintTools;
import us.ihmc.util.PeriodicNonRealtimeThreadScheduler;
import us.ihmc.util.PeriodicThreadScheduler;

public class QuadrupedSimulationFactory
{
   private final RequiredFactoryField<SDFFullQuadrupedRobotModel> fullRobotModel = new RequiredFactoryField<>("fullRobotModel");
   private final RequiredFactoryField<QuadrupedPhysicalProperties> physicalProperties = new RequiredFactoryField<>("physicalProperties");
   private final RequiredFactoryField<QuadrupedControlMode> controlMode = new RequiredFactoryField<>("controlMode");
   private final RequiredFactoryField<SDFRobot> sdfRobot = new RequiredFactoryField<>("sdfRobot");
   private final RequiredFactoryField<Double> controlDT = new RequiredFactoryField<>("controlDT");
   private final RequiredFactoryField<Double> gravity = new RequiredFactoryField<>("gravity");
   private final RequiredFactoryField<Integer> recordFrequency = new RequiredFactoryField<>("recordFrequency");
   private final RequiredFactoryField<Boolean> useTrackAndDolly = new RequiredFactoryField<>("useTrackAndDolly");
   private final RequiredFactoryField<Boolean> showPlotter = new RequiredFactoryField<>("showPlotter");
   private final RequiredFactoryField<QuadrupedModelFactory> modelFactory = new RequiredFactoryField<>("modelFactory");
   private final RequiredFactoryField<SimulationConstructionSetParameters> scsParameters = new RequiredFactoryField<>("scsParameters");
   private final RequiredFactoryField<QuadrupedGroundContactParameters> groundContactParameters = new RequiredFactoryField<>("groundContactParameters");
   private final RequiredFactoryField<QuadrupedSimulationInitialPositionParameters> initialPositionParameters = new RequiredFactoryField<>("initialPositionParameters");
   private final RequiredFactoryField<OutputWriter> outputWriter = new RequiredFactoryField<>("outputWriter");
   private final RequiredFactoryField<Boolean> useNetworking = new RequiredFactoryField<>("useNetworking");
   private final RequiredFactoryField<NetClassList> netClassList = new RequiredFactoryField<>("netClassList");
   private final RequiredFactoryField<SensorTimestampHolder> timestampProvider = new RequiredFactoryField<>("timestampProvider");
   private final RequiredFactoryField<Boolean> useStateEstimator = new RequiredFactoryField<>("useStateEstimator");
   private final RequiredFactoryField<QuadrupedSensorInformation> sensorInformation = new RequiredFactoryField<>("sensorInformation");
   private final RequiredFactoryField<StateEstimatorParameters> stateEstimatorParameters = new RequiredFactoryField<>("stateEstimatorParameters");
   private final RequiredFactoryField<QuadrupedReferenceFrames> referenceFrames = new RequiredFactoryField<>("referenceFrames");
   private final RequiredFactoryField<QuadrupedPositionBasedCrawlControllerParameters> positionBasedCrawlControllerParameters = new RequiredFactoryField<>("positionBasedCrawlControllerParameters");
   
   private final OptionalFactoryField<QuadrupedGroundContactModelType> groundContactModelType = new OptionalFactoryField<>("groundContactModelType");
   private final OptionalFactoryField<QuadrupedRobotControllerFactory> headControllerFactory = new OptionalFactoryField<>("headControllerFactory");
   private final OptionalFactoryField<GroundProfile3D> providedGroundProfile3D = new OptionalFactoryField<>("providedGroundProfile3D");
   
   // TO CONSTRUCT
   private YoGraphicsListRegistry yoGraphicsListRegistry;
   private YoGraphicsListRegistry yoGraphicsListRegistryForDetachedOverhead;
   private SensorReader sensorReader;
   private QuadrantDependentList<ContactablePlaneBody> contactableFeet;
   private QuadrantDependentList<FootSwitchInterface> footSwitches;
   private DRCKinematicsBasedStateEstimator stateEstimator;
   private PacketCommunicator packetCommunicator;
   private GlobalDataProducer globalDataProducer;
   private RobotController headController;
   private QuadrupedControllerManager controllerManager;
   private DRCPoseCommunicator poseCommunicator;
   private GroundProfile3D groundProfile3D;
   private LinearGroundContactModel groundContactModel;
   private QuadrupedSimulationController simulationController;
   private QuadrupedLegInverseKinematicsCalculator legInverseKinematicsCalculator;
   
   // CREATION
   
   private void setupYoRegistries()
   {
      yoGraphicsListRegistry = new YoGraphicsListRegistry();
      yoGraphicsListRegistry.setYoGraphicsUpdatedRemotely(true);
      yoGraphicsListRegistryForDetachedOverhead = new YoGraphicsListRegistry();
   }

   private void createSensorReader()
   {
      if (useStateEstimator.get())
      {
         SixDoFJoint rootInverseDynamicsJoint = fullRobotModel.get().getRootJoint();
         IMUDefinition[] imuDefinitions = fullRobotModel.get().getIMUDefinitions();
         ForceSensorDefinition[] forceSensorDefinitions = fullRobotModel.get().getForceSensorDefinitions();
         ContactSensorHolder contactSensorHolder = null;
         RawJointSensorDataHolderMap rawJointSensorDataHolderMap = null;
         DesiredJointDataHolder estimatorDesiredJointDataHolder = null;

         SimulatedSensorHolderAndReaderFromRobotFactory sensorReaderFactory;
         sensorReaderFactory = new SimulatedSensorHolderAndReaderFromRobotFactory(sdfRobot.get(), stateEstimatorParameters.get());
         sensorReaderFactory.build(rootInverseDynamicsJoint, imuDefinitions, forceSensorDefinitions, contactSensorHolder, rawJointSensorDataHolderMap,
                                   estimatorDesiredJointDataHolder, sdfRobot.get().getRobotsYoVariableRegistry());

         sensorReader = sensorReaderFactory.getSensorReader();
      }
      else
      {
         sensorReader = new SDFQuadrupedPerfectSimulatedSensor(sdfRobot.get(), fullRobotModel.get(), referenceFrames.get());
      }
   }
   
   private void createContactibleFeet()
   {
      QuadrupedFootContactableBodiesFactory footContactableBodiesFactory = new QuadrupedFootContactableBodiesFactory();
      footContactableBodiesFactory.setFullRobotModel(fullRobotModel.get());
      footContactableBodiesFactory.setPhysicalProperties(physicalProperties.get());
      footContactableBodiesFactory.setReferenceFrames(referenceFrames.get());
      contactableFeet = footContactableBodiesFactory.createFootContactableBodies();
   }
   
   private void createFootSwitches()
   {
      QuadrupedFootSwitchFactory footSwitchFactory = new QuadrupedFootSwitchFactory();
      footSwitchFactory.setFootContactableBodies(contactableFeet);
      footSwitchFactory.setFullRobotModel(fullRobotModel.get());
      footSwitchFactory.setGravity(gravity.get());
      footSwitchFactory.setYoVariableRegistry(sdfRobot.get().getRobotsYoVariableRegistry());
      footSwitchFactory.setFootSwitchType(FootSwitchType.TouchdownBased);
      footSwitches = footSwitchFactory.createFootSwitches();
   }
   
   private void createStateEstimator()
   {
      if (useStateEstimator.get())
      {
         QuadrupedStateEstimatorFactory stateEstimatorFactory = new QuadrupedStateEstimatorFactory();
         stateEstimatorFactory.setEstimatorDT(controlDT.get());
         stateEstimatorFactory.setFootContactableBodies(contactableFeet);
         stateEstimatorFactory.setFootSwitches(footSwitches);
         stateEstimatorFactory.setFullRobotModel(fullRobotModel.get());
         stateEstimatorFactory.setGravity(gravity.get());
         stateEstimatorFactory.setSensorInformation(sensorInformation.get());
         stateEstimatorFactory.setSensorOutputMapReadOnly(sensorReader.getSensorOutputMapReadOnly());
         stateEstimatorFactory.setStateEstimatorParameters(stateEstimatorParameters.get());
         stateEstimatorFactory.setYoGraphicsListRegistry(yoGraphicsListRegistry);
         stateEstimatorFactory.setYoVariableRegistry(sdfRobot.get().getRobotsYoVariableRegistry());
         stateEstimator = stateEstimatorFactory.createStateEstimator();
      }
      else
      {
         stateEstimator = null;
      }
   }
   
   private void createPacketCommunicator() throws IOException
   {
      if (useNetworking.get())
      {
         try
         {
            packetCommunicator = PacketCommunicator.createTCPPacketCommunicatorServer(NetworkPorts.CONTROLLER_PORT, netClassList.get());
            packetCommunicator.connect();
         }
         catch (BindException bindException)
         {
            PrintTools.error(this, bindException.getMessage());
            PrintTools.warn(this, "Continuing without networking");
            useNetworking.set(false);
         }
      }
   }
   
   private void createGlobalDataProducer()
   {
      if (useNetworking.get())
      {
         globalDataProducer = new QuadrupedGlobalDataProducer(packetCommunicator);
      }
   }
   
   private void createHeadController()
   {
      if (headControllerFactory.hasBeenSet())
      {
         headControllerFactory.get().setControlDt(controlDT.get());
         headControllerFactory.get().setFullRobotModel(fullRobotModel.get());
         headControllerFactory.get().setGlobalDataProducer(globalDataProducer);
      
         headController = headControllerFactory.get().createRobotController();
      }
   }
   
   private void createInverseKinematicsCalculator()
   {
      if (controlMode.get() == QuadrupedControlMode.POSITION || controlMode.get() == QuadrupedControlMode.POSITION_DEV)
      {
         legInverseKinematicsCalculator = new QuadrupedInverseKinematicsCalculators(modelFactory.get(), physicalProperties.get(), fullRobotModel.get(), referenceFrames.get(),
                                                                                    sdfRobot.get().getRobotsYoVariableRegistry(), yoGraphicsListRegistry);
      }
   }
   
   public void createControllerManager() throws IOException
   {
      
      QuadrupedRuntimeEnvironment runtimeEnvironment = new QuadrupedRuntimeEnvironment(controlDT.get(), sdfRobot.get().getYoTime(), fullRobotModel.get(), sdfRobot.get().getRobotsYoVariableRegistry(),
                                                           yoGraphicsListRegistry, yoGraphicsListRegistryForDetachedOverhead, globalDataProducer, footSwitches);
      switch (controlMode.get())
      {
      case FORCE:
         controllerManager = new QuadrupedForceControllerManager(runtimeEnvironment, physicalProperties.get());
         break;
      case FORCE_DEV:
         controllerManager = new QuadrupedForceDevelopmentControllerManager(runtimeEnvironment, physicalProperties.get());
         break;
      case POSITION:
         controllerManager = new QuadrupedPositionControllerManager(runtimeEnvironment, modelFactory.get(), physicalProperties.get(), initialPositionParameters.get(),
                                                                    positionBasedCrawlControllerParameters.get(), legInverseKinematicsCalculator);
         break;
      case POSITION_DEV:
         controllerManager = new QuadrupedPositionDevelopmentControllerManager(runtimeEnvironment, modelFactory.get(), physicalProperties.get(),
                                                                               initialPositionParameters.get(), legInverseKinematicsCalculator);
         break;
      default:
         controllerManager = null;
         break;
      }
   }
   
   private void createPoseCommunicator()
   {
      if (useNetworking.get())
      {
         JointConfigurationGatherer jointConfigurationGathererAndProducer = new JointConfigurationGatherer(fullRobotModel.get());
         PeriodicThreadScheduler scheduler = new PeriodicNonRealtimeThreadScheduler("PoseCommunicator");
         poseCommunicator = new DRCPoseCommunicator(fullRobotModel.get(), jointConfigurationGathererAndProducer, null, globalDataProducer,
                                                    timestampProvider.get(), sensorReader.getSensorRawOutputMapReadOnly(),
                                                    controllerManager.getMotionStatusHolder(), null, scheduler, netClassList.get());
      }
      else
      {
         poseCommunicator = null;
      }
   }
   
   private void createGroundContactModel()
   {
      if (groundContactModelType.hasBeenSet() && !providedGroundProfile3D.hasBeenSet())
      {
         switch (groundContactModelType.get())
         {
         case FLAT:
            groundProfile3D = new FlatGroundProfile(0.0);
            break;
         case ROLLING_HILLS:
         groundProfile3D =  new RollingGroundProfile(0.025, 1.0, 0.0, -20.0, 20.0, -20.0, 20.0);
            break;
         case ROTATABLE:
            groundProfile3D = new RotatablePlaneTerrainProfile(new Point3d(), sdfRobot.get(), yoGraphicsListRegistry, controlDT.get());
            break;
         case SLOPES:
            double xMin = -5.0, xMax = 40.0;
            double yMin = -5.0, yMax =  5.0;
            double[][] xSlopePairs = new double[][]
            {
               {1.0, 0.0}, {3.0, 0.1}
            };
            groundProfile3D = new AlternatingSlopesGroundProfile(xSlopePairs, xMin, xMax, yMin, yMax);
            break;
         case STEPPED:
            groundProfile3D = new VaryingStairGroundProfile(0.0, 0.0, new double[] {1.5, 1.0, 1.0, 0.5}, new double[] {0.0, 0.05, -0.1, 0.05, 0.05});
            break;
         default:
            groundProfile3D = null;
            break;
         }
      }
      else if (providedGroundProfile3D.hasBeenSet())
      {
         groundProfile3D =  providedGroundProfile3D.get();
      }
      else
      {
         groundProfile3D = new FlatGroundProfile(0.0);
      }
      
      groundContactModel = new LinearGroundContactModel(sdfRobot.get(), sdfRobot.get().getRobotsYoVariableRegistry());
      groundContactModel.setZStiffness(groundContactParameters.get().getZStiffness());
      groundContactModel.setZDamping(groundContactParameters.get().getZDamping());
      groundContactModel.setXYStiffness(groundContactParameters.get().getXYStiffness());
      groundContactModel.setXYDamping(groundContactParameters.get().getXYDamping());
      groundContactModel.setGroundProfile3D(groundProfile3D);
   }
   
   private void createSimulationController()
   {
      simulationController = new QuadrupedSimulationController(sdfRobot.get(), sensorReader, outputWriter.get(), controllerManager,
                                                               stateEstimator, poseCommunicator, headController);
   }
   
   private void setupSDFRobot()
   {
      sdfRobot.get().setController(simulationController);
      sdfRobot.get().setPositionInWorld(initialPositionParameters.get().getInitialBodyPosition());
      for (QuadrupedJointName quadrupedJointName : modelFactory.get().getQuadrupedJointNames())
      {
         OneDegreeOfFreedomJoint oneDegreeOfFreedomJoint = sdfRobot.get().getOneDegreeOfFreedomJoint(modelFactory.get().getSDFNameForJointName(quadrupedJointName));
         oneDegreeOfFreedomJoint.setQ(initialPositionParameters.get().getInitialJointPosition(quadrupedJointName));
      }
      try
      {
         sdfRobot.get().update();
         sdfRobot.get().doDynamicsButDoNotIntegrate();
         sdfRobot.get().update();
      }
      catch (UnreasonableAccelerationException unreasonableAccelerationException)
      {
         throw new RuntimeException("UnreasonableAccelerationException");
      }
      
      Point3d initialCoMPosition = new Point3d();
      double totalMass = sdfRobot.get().computeCenterOfMass(initialCoMPosition);
      
      if (useStateEstimator.get())
      {
         Quat4d initialEstimationLinkOrientation = new Quat4d();
         sdfRobot.get().getPelvisJoint().getJointTransform3D().getRotation(initialEstimationLinkOrientation);
         stateEstimator.initializeEstimatorToActual(initialCoMPosition, initialEstimationLinkOrientation);
      }
      
      sdfRobot.get().setGravity(gravity.get());
      sdfRobot.get().setGroundContactModel(groundContactModel);
      System.out.println("Total mass: " + totalMass);
   }
   
   public SimulationConstructionSet createSimulation() throws IOException
   {
      FactoryTools.checkAllRequiredFactoryFieldsAreSet(this);
      
      setupYoRegistries();
      createSensorReader();
      createContactibleFeet();
      createFootSwitches();
      createStateEstimator();
      createPacketCommunicator();
      createGlobalDataProducer();
      createHeadController();
      createInverseKinematicsCalculator();
      createControllerManager();
      createPoseCommunicator();
      createGroundContactModel();
      createSimulationController();
      setupSDFRobot();
      
      SimulationConstructionSet scs = new SimulationConstructionSet(sdfRobot.get(), scsParameters.get());
      if (groundContactModelType.hasBeenSet() && groundContactModelType.get() == QuadrupedGroundContactModelType.ROTATABLE)
      {
         scs.setGroundVisible(false);
      }
      scs.addYoGraphicsListRegistry(yoGraphicsListRegistry);
      scs.setDT(controlDT.get(), recordFrequency.get());
      if (scs.getSimulationConstructionSetParameters().getCreateGUI())
      {
         scs.setCameraTrackingVars("q_x", "q_y", "q_z");
         scs.setCameraDollyVars("q_x", "q_y", "q_z");
         scs.setCameraTracking(useTrackAndDolly.get(), useTrackAndDolly.get(), useTrackAndDolly.get(), useTrackAndDolly.get());
         scs.setCameraDolly(useTrackAndDolly.get(), useTrackAndDolly.get(), useTrackAndDolly.get(), false);
         scs.setCameraDollyOffsets(4.0, 4.0, 1.0);
         if (showPlotter.get())
         {
            VisualizerUtils.createOverheadPlotter(scs, false, "centerOfMass", yoGraphicsListRegistry);
            VisualizerUtils.createOverheadPlotterInSeparateWindow(scs, false, "centerOfMass", yoGraphicsListRegistryForDetachedOverhead);
            scs.getStandardSimulationGUI().selectPanel("Plotter");
         }
      }
      return scs;
   }
   
   // OPTIONS
   
   public void setControlDT(double controlDT)
   {
      this.controlDT.set(controlDT);
   }
   
   public void setGravity(double gravity)
   {
      this.gravity.set(gravity);
   }
   
   public void setRecordFrequency(int recordFrequency)
   {
      this.recordFrequency.set(recordFrequency);
   }
   
   public void setUseTrackAndDolly(boolean useTrackAndDolly)
   {
      this.useTrackAndDolly.set(useTrackAndDolly);
   }
   
   public void setShowPlotter(boolean showPlotter)
   {
      this.showPlotter.set(showPlotter);
   }
   
   public void setModelFactory(QuadrupedModelFactory modelFactory)
   {
      this.modelFactory.set(modelFactory);
   }
   
   public void setSCSParameters(SimulationConstructionSetParameters scsParameters)
   {
      this.scsParameters.set(scsParameters);
   }
   
   public void setGroundContactModelType(QuadrupedGroundContactModelType groundContactModelType)
   {
      this.groundContactModelType.set(groundContactModelType);
   }
   
   public void setGroundContactParameters(QuadrupedGroundContactParameters groundContactParameters)
   {
      this.groundContactParameters.set(groundContactParameters);
   }
   
   public void setHeadControllerFactory(QuadrupedRobotControllerFactory headControllerFactory)
   {
      this.headControllerFactory.set(headControllerFactory);
   }
   
   public void setOutputWriter(OutputWriter outputWriter)
   {
      this.outputWriter.set(outputWriter);
   }
   
   public void setInitialPositionParameters(QuadrupedSimulationInitialPositionParameters initialPositionParameters)
   {
      this.initialPositionParameters.set(initialPositionParameters);
   }
   
   public void setPhysicalProperties(QuadrupedPhysicalProperties physicalProperties)
   {
      this.physicalProperties.set(physicalProperties);
   }
   
   public void setControlMode(QuadrupedControlMode controlMode)
   {
      this.controlMode.set(controlMode);
   }
   
   public void setFullRobotModel(SDFFullQuadrupedRobotModel fullRobotModel)
   {
      this.fullRobotModel.set(fullRobotModel);
   }
   
   public void setUseNetworking(boolean useNetworking)
   {
      this.useNetworking.set(useNetworking);
   }
   
   public void setNetClassList(NetClassList netClassList)
   {
      this.netClassList.set(netClassList);
   }
   
   public void setTimestampHolder(SensorTimestampHolder timestampProvider)
   {
      this.timestampProvider.set(timestampProvider);
   }

   public void setSDFRobot(SDFRobot sdfRobot)
   {
      this.sdfRobot.set(sdfRobot);
   }
   
   public void setUseStateEstimator(boolean useStateEstimator)
   {
      this.useStateEstimator.set(useStateEstimator);
   }

   public void setSensorInformation(QuadrupedSensorInformation sensorInformation)
   {
      this.sensorInformation.set(sensorInformation);
   }

   public void setStateEstimatorParameters(StateEstimatorParameters stateEstimatorParameters)
   {
      this.stateEstimatorParameters.set(stateEstimatorParameters);
   }

   public void setReferenceFrames(QuadrupedReferenceFrames referenceFrames)
   {
      this.referenceFrames.set(referenceFrames);
   }
   
   public void setPositionBasedCrawlControllerParameters(QuadrupedPositionBasedCrawlControllerParameters positionBasedCrawlControllerParameters)
   {
      this.positionBasedCrawlControllerParameters.set(positionBasedCrawlControllerParameters);
   }
   
   public void setGroundProfile3D(GroundProfile3D groundProfile3D)
   {
      providedGroundProfile3D.set(groundProfile3D);
   }
}
