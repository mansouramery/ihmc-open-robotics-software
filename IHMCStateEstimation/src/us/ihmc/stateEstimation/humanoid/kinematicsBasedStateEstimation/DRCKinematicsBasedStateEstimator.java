package us.ihmc.stateEstimation.humanoid.kinematicsBasedStateEstimation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.vecmath.Quat4d;
import javax.vecmath.Tuple3d;

import org.ejml.data.DenseMatrix64F;

import us.ihmc.commonWalkingControlModules.sensors.footSwitch.FootSwitchInterface;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.humanoidRobotics.communication.packets.sensing.StateEstimatorModePacket.StateEstimatorMode;
import us.ihmc.humanoidRobotics.communication.subscribers.PelvisPoseCorrectionCommunicatorInterface;
import us.ihmc.humanoidRobotics.communication.subscribers.RequestWristForceSensorCalibrationSubscriber;
import us.ihmc.humanoidRobotics.communication.subscribers.StateEstimatorModeSubscriber;
import us.ihmc.humanoidRobotics.model.CenterOfPressureDataHolder;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.EnumYoVariable;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.TwistCalculator;
import us.ihmc.robotics.sensors.ForceSensorDataHolder;
import us.ihmc.robotics.sensors.ForceSensorDataHolderReadOnly;
import us.ihmc.robotics.time.TimeTools;
import us.ihmc.sensorProcessing.imu.FusedIMUSensor;
import us.ihmc.sensorProcessing.model.RobotMotionStatusHolder;
import us.ihmc.sensorProcessing.sensorProcessors.SensorOutputMapReadOnly;
import us.ihmc.sensorProcessing.stateEstimation.IMUSensorReadOnly;
import us.ihmc.sensorProcessing.stateEstimation.StateEstimator;
import us.ihmc.sensorProcessing.stateEstimation.StateEstimatorParameters;
import us.ihmc.sensorProcessing.stateEstimation.evaluation.FullInverseDynamicsStructure;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicReferenceFrame;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.stateEstimation.humanoid.DRCStateEstimatorInterface;

public class DRCKinematicsBasedStateEstimator implements DRCStateEstimatorInterface, StateEstimator
{
   public static final boolean INITIALIZE_HEIGHT_WITH_FOOT = true;

   public static final boolean USE_NEW_PELVIS_POSE_CORRECTOR = true;
   
   private final String name = getClass().getSimpleName();
   private final YoVariableRegistry registry = new YoVariableRegistry(name);
   private final DoubleYoVariable yoTime = new DoubleYoVariable("t_stateEstimator", registry);
   private final EnumYoVariable<StateEstimatorMode> operatingMode = new EnumYoVariable<>("stateEstimatorOperatingMode", registry, StateEstimatorMode.class, false);

   private final FusedIMUSensor fusedIMUSensor;
   private final JointStateUpdater jointStateUpdater;
   private final PelvisRotationalStateUpdater pelvisRotationalStateUpdater;
   private final PelvisLinearStateUpdater pelvisLinearStateUpdater;
   private final ForceSensorStateUpdater forceSensorStateUpdater;
   private final IMUBiasStateEstimator imuBiasStateEstimator;

   private final PelvisPoseHistoryCorrectionInterface pelvisPoseHistoryCorrection;

   private final double estimatorDT;

   private boolean visualizeMeasurementFrames = false;
   private final ArrayList<YoGraphicReferenceFrame> dynamicGraphicMeasurementFrames = new ArrayList<>();

   private final CenterOfPressureVisualizer copVisualizer;

   private final BooleanYoVariable usePelvisCorrector;
   private final SensorOutputMapReadOnly sensorOutputMapReadOnly;

   private StateEstimatorModeSubscriber stateEstimatorModeSubscriber = null;

   private final BooleanYoVariable reinitializeStateEstimator = new BooleanYoVariable("reinitializeStateEstimator", registry);

   public DRCKinematicsBasedStateEstimator(FullInverseDynamicsStructure inverseDynamicsStructure, StateEstimatorParameters stateEstimatorParameters,
         SensorOutputMapReadOnly sensorOutputMapReadOnly, ForceSensorDataHolder forceSensorDataHolderToUpdate, String[] imuSensorsToUseInStateEstimator,
         double gravitationalAcceleration, Map<RigidBody, FootSwitchInterface> footSwitches,
         CenterOfPressureDataHolder centerOfPressureDataHolderFromController, RobotMotionStatusHolder robotMotionStatusFromController,
         Map<RigidBody, ? extends ContactablePlaneBody> feet, YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      estimatorDT = stateEstimatorParameters.getEstimatorDT();
      this.sensorOutputMapReadOnly = sensorOutputMapReadOnly;

      usePelvisCorrector = new BooleanYoVariable("useExternalPelvisCorrector", registry);
      usePelvisCorrector.set(true);
      if(forceSensorDataHolderToUpdate != null)
      {
         forceSensorStateUpdater = new ForceSensorStateUpdater(sensorOutputMapReadOnly, forceSensorDataHolderToUpdate, stateEstimatorParameters, gravitationalAcceleration, yoGraphicsListRegistry, registry);
      }
      else
      {
         forceSensorStateUpdater = null;
      }

      if(USE_NEW_PELVIS_POSE_CORRECTOR)
         this.pelvisPoseHistoryCorrection = new NewPelvisPoseHistoryCorrection(inverseDynamicsStructure, stateEstimatorParameters.getEstimatorDT(), registry, yoGraphicsListRegistry, 1000);
      else
         this.pelvisPoseHistoryCorrection = new PelvisPoseHistoryCorrection(inverseDynamicsStructure, stateEstimatorParameters.getEstimatorDT(), registry, 1000);

      List<IMUSensorReadOnly> imuProcessedOutputs = new ArrayList<>();
      List<String> imuSensorsToUse = Arrays.asList(imuSensorsToUseInStateEstimator);
      for (IMUSensorReadOnly imu : sensorOutputMapReadOnly.getIMUProcessedOutputs())
      {
         if (imuSensorsToUse.contains(imu.getSensorName()))
            imuProcessedOutputs.add(imu);
      }

      List<IMUSensorReadOnly> imusToUse = new ArrayList<>();

      if (stateEstimatorParameters.createFusedIMUSensor())
      {
         if (imuProcessedOutputs.size() != 2)
            throw new RuntimeException("Cannot create FusedIMUSensor.");
         fusedIMUSensor = new FusedIMUSensor(imuProcessedOutputs.get(0), imuProcessedOutputs.get(1), estimatorDT,
               stateEstimatorParameters.getIMUYawDriftFilterFreqInHertz(), registry);
         imusToUse.add(fusedIMUSensor);
      }
      else
      {
         fusedIMUSensor = null;
         imusToUse.addAll(imuProcessedOutputs);
      }

      TwistCalculator twistCalculator = inverseDynamicsStructure.getTwistCalculator();
      boolean isAccelerationIncludingGravity = stateEstimatorParameters.cancelGravityFromAccelerationMeasurement();
      imuBiasStateEstimator = new IMUBiasStateEstimator(imuProcessedOutputs, feet.keySet(), twistCalculator, gravitationalAcceleration, isAccelerationIncludingGravity, estimatorDT, registry);
      imuBiasStateEstimator.configureModuleParameters(stateEstimatorParameters);

      jointStateUpdater = new JointStateUpdater(inverseDynamicsStructure, sensorOutputMapReadOnly, stateEstimatorParameters, registry);
      pelvisRotationalStateUpdater = new PelvisRotationalStateUpdater(inverseDynamicsStructure, imusToUse, imuBiasStateEstimator, estimatorDT, registry);
      
      pelvisLinearStateUpdater = new PelvisLinearStateUpdater(inverseDynamicsStructure, imusToUse, imuBiasStateEstimator, footSwitches, centerOfPressureDataHolderFromController, feet, gravitationalAcceleration, yoTime,
            stateEstimatorParameters, yoGraphicsListRegistry, registry);


      if (yoGraphicsListRegistry != null)
      {
         copVisualizer = new CenterOfPressureVisualizer(footSwitches, yoGraphicsListRegistry, registry);
      }
      else
      {
         copVisualizer = null;
      }

      visualizeMeasurementFrames = visualizeMeasurementFrames && yoGraphicsListRegistry != null;

      List<IMUSensorReadOnly> imusToDisplay = new ArrayList<>();
      imusToDisplay.addAll(imuProcessedOutputs);
      if (fusedIMUSensor != null)
         imusToDisplay.add(fusedIMUSensor);

      if (visualizeMeasurementFrames)
         setupDynamicGraphicObjects(yoGraphicsListRegistry, imusToDisplay);
   }

   private void setupDynamicGraphicObjects(YoGraphicsListRegistry yoGraphicsListRegistry, List<? extends IMUSensorReadOnly> imuProcessedOutputs)
   {
      for (int i = 0; i < imuProcessedOutputs.size(); i++)
      {
         YoGraphicReferenceFrame dynamicGraphicMeasurementFrame = new YoGraphicReferenceFrame(imuProcessedOutputs.get(i).getMeasurementFrame(), registry, 1.0);
         dynamicGraphicMeasurementFrames.add(dynamicGraphicMeasurementFrame);
      }
      yoGraphicsListRegistry.registerYoGraphics("imuFrame", dynamicGraphicMeasurementFrames);
   }

   @Override
   public StateEstimator getStateEstimator()
   {
      return this;
   }

   @Override
   public void initialize()
   {
      if (fusedIMUSensor != null)
         fusedIMUSensor.update();

      operatingMode.set(StateEstimatorMode.NORMAL);

      jointStateUpdater.initialize();
      pelvisRotationalStateUpdater.initialize();
      if(forceSensorStateUpdater != null)
      {
         forceSensorStateUpdater.initialize();
      }
      pelvisLinearStateUpdater.initialize();

      imuBiasStateEstimator.initialize();
   }

   @Override
   public void doControl()
   {
      if(reinitializeStateEstimator.getBooleanValue())
      {
         reinitializeStateEstimator.set(false);
         initialize();
      }
      yoTime.set(TimeTools.nanoSecondstoSeconds(sensorOutputMapReadOnly.getTimestamp()));

      if (fusedIMUSensor != null)
         fusedIMUSensor.update();

      if (stateEstimatorModeSubscriber != null && stateEstimatorModeSubscriber.checkForNewOperatingModeRequest())
      {
         operatingMode.set(stateEstimatorModeSubscriber.getRequestedOperatingMode());
      }

      jointStateUpdater.updateJointState();

      switch (operatingMode.getEnumValue())
      {
         case FROZEN:
            pelvisRotationalStateUpdater.updateForFrozenState();
            if(forceSensorStateUpdater != null)
            {
               forceSensorStateUpdater.updateForceSensorState();
            }
            pelvisLinearStateUpdater.updateForFrozenState();
            break;

         case NORMAL:
         default:
            pelvisRotationalStateUpdater.updateRootJointOrientationAndAngularVelocity();
            if(forceSensorStateUpdater != null)
            {
               forceSensorStateUpdater.updateForceSensorState();
            }
            pelvisLinearStateUpdater.updateRootJointPositionAndLinearVelocity();

            List<RigidBody> trustedFeet = pelvisLinearStateUpdater.getCurrentListOfTrustedFeet();
            imuBiasStateEstimator.compute(trustedFeet);
            break;
      }

      if (usePelvisCorrector.getBooleanValue() && pelvisPoseHistoryCorrection != null)
      {
         pelvisPoseHistoryCorrection.doControl(sensorOutputMapReadOnly.getVisionSensorTimestamp());
      }

      updateVisualizers();
   }

   private void updateVisualizers()
   {
      if (copVisualizer != null)
         copVisualizer.update();

      if (visualizeMeasurementFrames)
      {
         for (int i = 0; i < dynamicGraphicMeasurementFrames.size(); i++)
            dynamicGraphicMeasurementFrames.get(i).update();
      }
   }

   @Override
   public void initializeEstimatorToActual(Tuple3d initialCoMPosition, Quat4d initialEstimationLinkOrientation)
   {
      pelvisLinearStateUpdater.initializeCoMPositionToActual(initialCoMPosition);
      // Do nothing for the orientation since the IMU is trusted
   }

   @Override
   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }

   @Override
   public String getName()
   {
      return name;
   }

   @Override
   public String getDescription()
   {
      return getName();
   }

   @Override
   public void getEstimatedOrientation(FrameOrientation estimatedOrientationToPack)
   {
      pelvisRotationalStateUpdater.getEstimatedOrientation(estimatedOrientationToPack);
   }

   @Override
   public void setEstimatedOrientation(FrameOrientation estimatedOrientation)
   {
      // Do nothing, IMU is trusted
   }

   @Override
   public void getEstimatedAngularVelocity(FrameVector estimatedAngularVelocityToPack)
   {
      pelvisRotationalStateUpdater.getEstimatedAngularVelocity(estimatedAngularVelocityToPack);
   }

   @Override
   public void setEstimatedAngularVelocity(FrameVector estimatedAngularVelocity)
   {
      // Do nothing, IMU is trusted
   }

   @Override
   public void getEstimatedCoMPosition(FramePoint estimatedCoMPositionToPack)
   {
      pelvisLinearStateUpdater.getEstimatedCoMPosition(estimatedCoMPositionToPack);
   }

   @Override
   public void setEstimatedCoMPosition(FramePoint estimatedCoMPosition)
   {
      pelvisLinearStateUpdater.initializeCoMPositionToActual(estimatedCoMPosition);
   }

   @Override
   public void getEstimatedCoMVelocity(FrameVector estimatedCoMVelocityToPack)
   {
      pelvisLinearStateUpdater.getEstimatedCoMVelocity(estimatedCoMVelocityToPack);
   }

   @Override
   public void setEstimatedCoMVelocity(FrameVector estimatedCoMVelocity)
   {
   }

   @Override
   public void getEstimatedPelvisPosition(FramePoint estimatedPelvisPositionToPack)
   {
      pelvisLinearStateUpdater.getEstimatedPelvisPosition(estimatedPelvisPositionToPack);
   }

   @Override
   public void getEstimatedPelvisLinearVelocity(FrameVector estimatedPelvisLinearVelocityToPack)
   {
      pelvisLinearStateUpdater.getEstimatedPelvisLinearVelocity(estimatedPelvisLinearVelocityToPack);
   }

   @Override
   public DenseMatrix64F getCovariance()
   {
      return null;
   }

   @Override
   public DenseMatrix64F getState()
   {
      return null;
   }

   @Override
   public void setState(DenseMatrix64F x, DenseMatrix64F covariance)
   {
   }

   @Override
   public void initializeOrientationEstimateToMeasurement()
   {
      // Do nothing
   }

   public ForceSensorDataHolderReadOnly getForceSensorOutput()
   {
      return forceSensorStateUpdater.getForceSensorOutput();
   }

   public ForceSensorDataHolderReadOnly getForceSensorOutputWithGravityCancelled()
   {
      return forceSensorStateUpdater.getForceSensorOutputWithGravityCancelled();
   }

   public void setExternalPelvisCorrectorSubscriber(PelvisPoseCorrectionCommunicatorInterface externalPelvisPoseSubscriber)
   {
      pelvisPoseHistoryCorrection.setExternalPelvisCorrectorSubscriber(externalPelvisPoseSubscriber);
   }

   public void setOperatingModeSubscriber(StateEstimatorModeSubscriber stateEstimatorModeSubscriber)
   {
      this.stateEstimatorModeSubscriber = stateEstimatorModeSubscriber;
   }

   public void setRequestWristForceSensorCalibrationSubscriber(RequestWristForceSensorCalibrationSubscriber requestWristForceSensorCalibrationSubscriber)
   {
      forceSensorStateUpdater.setRequestWristForceSensorCalibrationSubscriber(requestWristForceSensorCalibrationSubscriber);
   }

   public ForceSensorCalibrationModule getForceSensorCalibrationModule()
   {
      return forceSensorStateUpdater;
   }
}
