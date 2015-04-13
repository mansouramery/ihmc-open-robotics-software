package us.ihmc.humanoidBehaviors;

import java.io.IOException;
import java.util.Arrays;

import us.ihmc.SdfLoader.SDFFullRobotModel;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.communication.kryo.IHMCCommunicationKryoNetClassList;
import us.ihmc.communication.packetCommunicator.PacketCommunicator;
import us.ihmc.communication.packets.behaviors.HumanoidBehaviorControlModePacket;
import us.ihmc.communication.packets.behaviors.HumanoidBehaviorType;
import us.ihmc.communication.packets.behaviors.HumanoidBehaviorTypePacket;
import us.ihmc.communication.packets.dataobjects.RobotConfigurationData;
import us.ihmc.communication.packets.walking.CapturabilityBasedStatus;
import us.ihmc.communication.subscribers.CapturabilityBasedStatusSubscriber;
import us.ihmc.communication.subscribers.RobotDataReceiver;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.humanoidBehaviors.behaviors.DrillPickUpBehavior;
import us.ihmc.humanoidBehaviors.behaviors.LocalizationBehavior;
import us.ihmc.humanoidBehaviors.behaviors.LocalizeDrillBehavior;
import us.ihmc.humanoidBehaviors.behaviors.ReceiveImageBehavior;
import us.ihmc.humanoidBehaviors.behaviors.RemoveMultipleDebrisBehavior;
import us.ihmc.humanoidBehaviors.behaviors.TurnValveBehavior;
import us.ihmc.humanoidBehaviors.behaviors.WalkToGoalBehavior;
import us.ihmc.humanoidBehaviors.behaviors.diagnostic.DiagnosticBehavior;
import us.ihmc.humanoidBehaviors.behaviors.midLevel.PushButtonBehavior;
import us.ihmc.humanoidBehaviors.behaviors.scripts.ScriptBehavior;
import us.ihmc.humanoidBehaviors.behaviors.simpleBehaviors.SimpleDoNothingBehavior;
import us.ihmc.humanoidBehaviors.communication.BehaviorCommunicationBridge;
import us.ihmc.humanoidBehaviors.communication.OutgoingCommunicationBridgeInterface;
import us.ihmc.humanoidBehaviors.dispatcher.BehaviorDisptacher;
import us.ihmc.humanoidBehaviors.dispatcher.HumanoidBehaviorControlModeSubscriber;
import us.ihmc.humanoidBehaviors.dispatcher.HumanoidBehaviorTypeSubscriber;
import us.ihmc.humanoidBehaviors.utilities.CapturePointUpdatable;
import us.ihmc.humanoidBehaviors.utilities.WristForceSensorFilteredUpdatable;
import us.ihmc.multicastLogDataProtocol.modelLoaders.LogModelProvider;
import us.ihmc.robotDataCommunication.YoVariableServer;
import us.ihmc.robotDataCommunication.logger.LogSettings;
import us.ihmc.sensorProcessing.parameters.DRCRobotSensorInformation;
import us.ihmc.util.PeriodicNonRealtimeThreadScheduler;
import us.ihmc.util.PeriodicThreadScheduler;
import us.ihmc.utilities.humanoidRobot.frames.ReferenceFrames;
import us.ihmc.utilities.humanoidRobot.model.ForceSensorDataHolder;
import us.ihmc.utilities.io.printing.PrintTools;
import us.ihmc.utilities.robotSide.RobotSide;
import us.ihmc.utilities.robotSide.SideDependentList;
import us.ihmc.wholeBodyController.WholeBodyControllerParameters;
import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;
import us.ihmc.yoUtilities.dataStructure.variable.BooleanYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.DoubleYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.EnumYoVariable;
import us.ihmc.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.yoUtilities.math.frames.YoFrameConvexPolygon2d;

public class IHMCHumanoidBehaviorManager
{
   public static final double BEHAVIOR_YO_VARIABLE_SERVER_DT = 0.01;

   private static double runAutomaticDiagnosticTimeToWait = Double.NaN;

   private final PacketCommunicator behaviorPacketCommunicator = PacketCommunicator.createIntraprocessPacketCommunicator(NetworkPorts.BEHAVIOUR_MODULE_PORT, new IHMCCommunicationKryoNetClassList());

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final DoubleYoVariable yoTime = new DoubleYoVariable("yoTime", registry);

   private YoVariableServer yoVariableServer = null;

   public IHMCHumanoidBehaviorManager(WholeBodyControllerParameters wholeBodyControllerParameters, LogModelProvider modelProvider, boolean startYoVariableServer,
         DRCRobotSensorInformation sensorInfo) throws IOException
   {
      this(wholeBodyControllerParameters, modelProvider, startYoVariableServer, sensorInfo, false);
   }

   public static void setAutomaticDiagnosticTimeToWait(double timeToWait)
   {
      runAutomaticDiagnosticTimeToWait = timeToWait;
   }

   private IHMCHumanoidBehaviorManager(WholeBodyControllerParameters wholeBodyControllerParameters, LogModelProvider modelProvider, boolean startYoVariableServer,
         DRCRobotSensorInformation sensorInfo, boolean runAutomaticDiagnostic) throws IOException
   {
      System.out.println(PrintTools.INFO + getClass().getSimpleName() + ": Initializing");

      if (startYoVariableServer)
      {
         PeriodicThreadScheduler scheduler = new PeriodicNonRealtimeThreadScheduler("BehaviorScheduler");
         yoVariableServer = new YoVariableServer(getClass(), scheduler, modelProvider, LogSettings.BEHAVIOR, BEHAVIOR_YO_VARIABLE_SERVER_DT);
      }

      SDFFullRobotModel fullRobotModel = wholeBodyControllerParameters.createFullRobotModel();
      WalkingControllerParameters walkingControllerParameters = wholeBodyControllerParameters.getWalkingControllerParameters();

      BehaviorCommunicationBridge communicationBridge = new BehaviorCommunicationBridge(behaviorPacketCommunicator, registry);

      YoGraphicsListRegistry yoGraphicsListRegistry = new YoGraphicsListRegistry();
      yoGraphicsListRegistry.setYoGraphicsUpdatedRemotely(false);
      ForceSensorDataHolder forceSensorDataHolder = new ForceSensorDataHolder(Arrays.asList(fullRobotModel.getForceSensorDefinitions()));
      RobotDataReceiver robotDataReceiver = new RobotDataReceiver(fullRobotModel, forceSensorDataHolder);

      ReferenceFrames referenceFrames = robotDataReceiver.getReferenceFrames();
      behaviorPacketCommunicator.attachListener(RobotConfigurationData.class, robotDataReceiver);

      HumanoidBehaviorControlModeSubscriber desiredBehaviorControlSubscriber = new HumanoidBehaviorControlModeSubscriber();
      HumanoidBehaviorTypeSubscriber desiredBehaviorSubscriber = new HumanoidBehaviorTypeSubscriber();

      BehaviorDisptacher dispatcher = new BehaviorDisptacher(yoTime, robotDataReceiver, desiredBehaviorControlSubscriber, desiredBehaviorSubscriber,
            communicationBridge, yoVariableServer, registry, yoGraphicsListRegistry);

      CapturabilityBasedStatusSubscriber capturabilityBasedStatusSubsrciber = new CapturabilityBasedStatusSubscriber();
      behaviorPacketCommunicator.attachListener(CapturabilityBasedStatus.class, capturabilityBasedStatusSubsrciber);

      CapturePointUpdatable capturePointUpdatable = new CapturePointUpdatable(capturabilityBasedStatusSubsrciber, yoGraphicsListRegistry, registry);
      dispatcher.addUpdatable(capturePointUpdatable);

      //      DoubleYoVariable minIcpDistanceToSupportPolygon = capturePointUpdatable.getMinIcpDistanceToSupportPolygon();
      //      DoubleYoVariable icpError = capturePointUpdatable.getIcpError();

      SideDependentList<WristForceSensorFilteredUpdatable> wristSensorUpdatables = null;
      if (sensorInfo.getWristForceSensorNames() != null && !sensorInfo.getWristForceSensorNames().containsValue(null))
      {
         wristSensorUpdatables = new SideDependentList<WristForceSensorFilteredUpdatable>();
         for (RobotSide robotSide : RobotSide.values)
         {
            WristForceSensorFilteredUpdatable wristSensorUpdatable = new WristForceSensorFilteredUpdatable(robotSide, fullRobotModel, sensorInfo,
                  forceSensorDataHolder, BEHAVIOR_YO_VARIABLE_SERVER_DT, behaviorPacketCommunicator, registry);
            wristSensorUpdatables.put(robotSide, wristSensorUpdatable);
            dispatcher.addUpdatable(wristSensorUpdatable);
         }
      }

      if (runAutomaticDiagnostic && !Double.isNaN(runAutomaticDiagnosticTimeToWait) && !Double.isInfinite(runAutomaticDiagnosticTimeToWait))
      {
         createAndRegisterAutomaticDiagnostic(dispatcher, fullRobotModel, referenceFrames, yoTime, communicationBridge, capturePointUpdatable,
               walkingControllerParameters, runAutomaticDiagnosticTimeToWait);
      }
      else
      {
         createAndRegisterBehaviors(dispatcher, fullRobotModel, wristSensorUpdatables, referenceFrames, yoTime, communicationBridge, yoGraphicsListRegistry,
               capturePointUpdatable, wholeBodyControllerParameters, walkingControllerParameters);
      }

      behaviorPacketCommunicator.attachListener(HumanoidBehaviorControlModePacket.class, desiredBehaviorControlSubscriber);
      behaviorPacketCommunicator.attachListener(HumanoidBehaviorTypePacket.class, desiredBehaviorSubscriber);

      behaviorPacketCommunicator.connect();
      
      if (startYoVariableServer)
      {
         yoVariableServer.setMainRegistry(registry, fullRobotModel, yoGraphicsListRegistry);
         yoVariableServer.start();
      }

      dispatcher.start();
   }

   /**
    * Create the different behaviors and register them in the dispatcher.
    * When creating a new behavior, that's where you need to add it.
    * @param fullRobotModel Holds the robot data (like joint angles). The data is updated in the dispatcher and can be shared with the behaviors.
    * @param referenceFrames Give access to useful references related to the robot. They're automatically updated.
    * @param yoTime Holds the controller time. It is updated in the dispatcher and can be shared with the behaviors.
    * @param outgoingCommunicationBridge used to send packets to the controller.
    * @param yoGraphicsListRegistry Allows to register YoGraphics that will be displayed in SCS.
    * @param wholeBodyControllerParameters 
    * @param forceSensorDataHolder Holds the force sensor data
    * @param ankleHeight 
    */
   private void createAndRegisterBehaviors(BehaviorDisptacher dispatcher, SDFFullRobotModel fullRobotModel,
         SideDependentList<WristForceSensorFilteredUpdatable> wristSensors, ReferenceFrames referenceFrames, DoubleYoVariable yoTime,
         OutgoingCommunicationBridgeInterface outgoingCommunicationBridge, YoGraphicsListRegistry yoGraphicsListRegistry,
         CapturePointUpdatable capturePointUpdatable, WholeBodyControllerParameters wholeBodyControllerParameters,
         WalkingControllerParameters walkingControllerParameters)
   {
      BooleanYoVariable tippingDetectedBoolean = capturePointUpdatable.getTippingDetectedBoolean();
      BooleanYoVariable yoDoubleSupport = capturePointUpdatable.getYoDoubleSupport();
      EnumYoVariable<RobotSide> yoSupportLeg = capturePointUpdatable.getYoSupportLeg();
      YoFrameConvexPolygon2d yoSupportPolygon = capturePointUpdatable.getYoSupportPolygon();

      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.DO_NOTHING, new SimpleDoNothingBehavior(outgoingCommunicationBridge));

      ScriptBehavior scriptBehavior = new ScriptBehavior(outgoingCommunicationBridge, fullRobotModel, yoTime, yoDoubleSupport, walkingControllerParameters);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.SCRIPT, scriptBehavior);

      DiagnosticBehavior diagnosticBehavior = new DiagnosticBehavior(fullRobotModel, yoSupportLeg, referenceFrames, yoTime, yoDoubleSupport,
            outgoingCommunicationBridge, walkingControllerParameters, yoSupportPolygon);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.DIAGNOSTIC, diagnosticBehavior);

      LocalizationBehavior localizationBehavior = new LocalizationBehavior(outgoingCommunicationBridge, fullRobotModel, yoTime, yoDoubleSupport, walkingControllerParameters);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.LOCALIZATION, localizationBehavior);

      TurnValveBehavior walkAndTurnValveBehavior = new TurnValveBehavior(outgoingCommunicationBridge, fullRobotModel, referenceFrames, yoTime,
            tippingDetectedBoolean, yoDoubleSupport, wholeBodyControllerParameters, walkingControllerParameters);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.WALK_N_TURN_VALVE, walkAndTurnValveBehavior);

      RemoveMultipleDebrisBehavior removeDebrisBehavior = new RemoveMultipleDebrisBehavior(outgoingCommunicationBridge, fullRobotModel, referenceFrames,
            wristSensors, yoTime, wholeBodyControllerParameters, walkingControllerParameters);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.DEBRIS_TASK, removeDebrisBehavior);

      WalkToGoalBehavior walkToGoalBehavior = new WalkToGoalBehavior(outgoingCommunicationBridge, fullRobotModel, yoTime,
            walkingControllerParameters.getAnkleHeight());
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.WALK_TO_GOAL, walkToGoalBehavior);

      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.RECEIVE_IMAGE, new ReceiveImageBehavior(outgoingCommunicationBridge));
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.LOCALIZE_DRILL, new LocalizeDrillBehavior(outgoingCommunicationBridge, referenceFrames));
      
      PushButtonBehavior pushButtonBehavior = new PushButtonBehavior(outgoingCommunicationBridge, referenceFrames, yoTime, wristSensors);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.PUSH_BUTTON, pushButtonBehavior);
      
      if(wholeBodyControllerParameters.createWholeBodyIkSolver() != null)
      {
            DrillPickUpBehavior drillPickUpBehavior = new DrillPickUpBehavior(outgoingCommunicationBridge, yoTime, fullRobotModel, referenceFrames,
               wholeBodyControllerParameters);
         dispatcher.addHumanoidBehavior(HumanoidBehaviorType.DRILL_PICK_UP, drillPickUpBehavior);
      }
   }

   private void createAndRegisterAutomaticDiagnostic(BehaviorDisptacher dispatcher, SDFFullRobotModel fullRobotModel,
         ReferenceFrames referenceFrames, DoubleYoVariable yoTime, OutgoingCommunicationBridgeInterface outgoingCommunicationBridge,
         CapturePointUpdatable capturePointUpdatable, WalkingControllerParameters walkingControllerParameters, double timeToWait)
    {
      BooleanYoVariable yoDoubleSupport = capturePointUpdatable.getYoDoubleSupport();
      EnumYoVariable<RobotSide> yoSupportLeg = capturePointUpdatable.getYoSupportLeg();
      YoFrameConvexPolygon2d yoSupportPolygon = capturePointUpdatable.getYoSupportPolygon();

      DiagnosticBehavior diagnosticBehavior = new DiagnosticBehavior(fullRobotModel, yoSupportLeg, referenceFrames, yoTime, yoDoubleSupport,
            outgoingCommunicationBridge, walkingControllerParameters, yoSupportPolygon);
      diagnosticBehavior.setupForAutomaticDiagnostic(timeToWait);
      dispatcher.addHumanoidBehavior(HumanoidBehaviorType.DIAGNOSTIC, diagnosticBehavior);
      dispatcher.requestBehavior(HumanoidBehaviorType.DIAGNOSTIC);
    }


   public static IHMCHumanoidBehaviorManager createBehaviorModuleForAutomaticDiagnostic(WholeBodyControllerParameters wholeBodyControllerParameters, LogModelProvider modelProvider, boolean startYoVariableServer,
         DRCRobotSensorInformation sensorInfo, double timeToWait) throws IOException
   {
      IHMCHumanoidBehaviorManager.setAutomaticDiagnosticTimeToWait(timeToWait);
      IHMCHumanoidBehaviorManager ihmcHumanoidBehaviorManager = new IHMCHumanoidBehaviorManager(wholeBodyControllerParameters, modelProvider, startYoVariableServer, sensorInfo, true);
      return ihmcHumanoidBehaviorManager;
   }
}