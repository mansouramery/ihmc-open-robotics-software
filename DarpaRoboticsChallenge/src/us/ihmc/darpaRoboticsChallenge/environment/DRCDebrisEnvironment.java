package us.ihmc.darpaRoboticsChallenge.environment;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import us.ihmc.graphics3DAdapter.GroundProfile3D;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.robotics.Axis;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.geometry.RotationTools;
import us.ihmc.robotics.geometry.TransformTools;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.simulationconstructionset.ExternalForcePoint;
import us.ihmc.simulationconstructionset.GroundContactModel;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.robotController.ContactController;
import us.ihmc.simulationconstructionset.util.LinearStickSlipGroundContactModel;
import us.ihmc.simulationconstructionset.util.environments.ContactableSelectableBoxRobot;
import us.ihmc.simulationconstructionset.util.environments.SelectableObjectListener;
import us.ihmc.simulationconstructionset.util.ground.CombinedTerrainObject3D;
import us.ihmc.simulationconstructionset.util.ground.TerrainObject3D;

public class DRCDebrisEnvironment implements CommonAvatarEnvironmentInterface
{
   private final ReferenceFrame constructionWorldFrame = ReferenceFrame.constructAWorldFrame("constructionFrame");

   private final CombinedTerrainObject3D combinedTerrainObject;
   private final ArrayList<ExternalForcePoint> contactPoints = new ArrayList<ExternalForcePoint>();
   private final List<ContactableSelectableBoxRobot> debrisRobots = new ArrayList<ContactableSelectableBoxRobot>();

   private final String debrisName = "Debris";
   private int id = 0;

   private final double debrisDepth = 0.0508;
   private final double debrisWidth = 0.1016;
   private final double debrisLength = 0.9144;

   private final double debrisMass = 1.0;

   private final PoseReferenceFrame robotInitialPoseReferenceFrame;

   private final double forceVectorScale = 1.0 / 50.0;

   public DRCDebrisEnvironment()
   {
      this(new Vector3d(0.0, 0.0, 0.0), 0.0);
   }

   public DRCDebrisEnvironment(Tuple3d robotInitialPosition, double robotInitialYaw)
   {
      combinedTerrainObject = new CombinedTerrainObject3D(getClass().getSimpleName());
      combinedTerrainObject.addTerrainObject(setUpGround("Ground"));

      Quat4d robotInitialOrientation = new Quat4d();
      RotationTools.convertYawPitchRollToQuaternion(robotInitialYaw, 0.0, 0.0, robotInitialOrientation);
      Point3d robotPosition = new Point3d(robotInitialPosition);
      FramePose robotInitialPose = new FramePose(constructionWorldFrame, robotPosition, robotInitialOrientation);

      this.robotInitialPoseReferenceFrame = new PoseReferenceFrame("robotInitialPoseReferenceFrame", robotInitialPose);
   }

   public void addStandingDebris(double xRelativeToRobot, double yRelativeToRobot, double debrisYaw)
   {
      Point3d tempPosition = new Point3d(xRelativeToRobot, yRelativeToRobot, debrisLength / 2.0);
      FramePose debrisPose = generateDebrisPose(tempPosition, debrisYaw, 0.0, 0.0);
      debrisRobots.add(createDebrisRobot(debrisPose));
   }

   public void addHorizontalDebrisLeaningOnTwoBoxes(Point3d positionOfCenterOfDebrisWithRespectToRobot, double debrisYaw, double debrisRoll)
   {
      double supportWidth = 0.1;
      double supportLength = 0.2;
      double supportHeight;

      double x;
      double y;
      double z;

      FramePose debrisPose = generateDebrisPose(positionOfCenterOfDebrisWithRespectToRobot, debrisYaw, 0.0, debrisRoll);
      debrisRobots.add(createDebrisRobot(debrisPose));

      RigidBodyTransform debrisTransform = new RigidBodyTransform();
      debrisPose.getPose(debrisTransform );
      TransformTools.rotate(debrisTransform, -debrisRoll, Axis.X);
      debrisPose.setPose(debrisTransform);
      debrisPose.setZ(0.0);
      PoseReferenceFrame debrisReferenceFrame = new PoseReferenceFrame("debrisReferenceFrame", debrisPose);

      //add first support
      FramePose firstSupportPose = new FramePose(debrisReferenceFrame);
      supportHeight = positionOfCenterOfDebrisWithRespectToRobot.getZ() - debrisWidth / 2.0 + debrisLength / 2.0 * Math.cos(debrisRoll);

      x = 0.0;
      y = -debrisLength / 2.0 * Math.sin(debrisRoll);
      z = supportHeight / 2.0;
      firstSupportPose.setPosition(x, y, z);
      firstSupportPose.changeFrame(constructionWorldFrame);
      RigidBodyTransform firstSupportTransform = new RigidBodyTransform();
      firstSupportPose.getPose(firstSupportTransform);
      combinedTerrainObject.addRotatableBox(firstSupportTransform, supportLength, supportWidth, supportHeight, YoAppearance.AliceBlue());

      //add second support
      FramePose secondSupportPose = new FramePose(debrisReferenceFrame);
      supportHeight = positionOfCenterOfDebrisWithRespectToRobot.getZ() - debrisWidth / 2.0 - debrisLength / 2.0 * Math.cos(debrisRoll);

      x = 0.0;
      y = debrisLength / 2.0 * Math.sin(debrisRoll);
      z = supportHeight / 2.0;
      secondSupportPose.setPosition(x, y, z);
      secondSupportPose.changeFrame(constructionWorldFrame);
      RigidBodyTransform secondSupportTransform = new RigidBodyTransform();
      secondSupportPose.getPose(secondSupportTransform);
      combinedTerrainObject.addRotatableBox(secondSupportTransform, supportLength, supportWidth, supportHeight, YoAppearance.AliceBlue());
   }

   public void addVerticalDebrisLeaningAgainstAWall(double xRelativeToRobot, double yRelativeToRobot, double debrisYaw, double debrisPitch)
   {
      Point3d tempPosition = new Point3d(xRelativeToRobot, yRelativeToRobot, debrisLength / 2.0 * Math.cos(debrisPitch));
      FramePose debrisPose = generateDebrisPose(tempPosition, debrisYaw, debrisPitch, 0.0);
      debrisRobots.add(createDebrisRobot(debrisPose));

      double supportWidth = 0.1;
      double supportLength = 0.2;
      double supportHeight = 1.05*debrisLength;

      RigidBodyTransform debrisTransform = new RigidBodyTransform();
      debrisPose.getPose(debrisTransform );
      TransformTools.rotate(debrisTransform, -debrisPitch, Axis.Y);
      debrisPose.setPose(debrisTransform);
      debrisPose.setZ(0.0);
      PoseReferenceFrame debrisReferenceFrame = new PoseReferenceFrame("debrisReferenceFrame", debrisPose);
      
      FramePose supportPose = new FramePose(debrisReferenceFrame);
      
      double x = supportWidth / 2.0 + debrisLength/2.0 * Math.sin(debrisPitch);
      double y = 0.0;
      double z = supportHeight / 2.0;
      
      supportPose.setPosition(x, y, z);
      supportPose.changeFrame(constructionWorldFrame);
    
      RigidBodyTransform supportTransform = new RigidBodyTransform();
      supportPose.getPose(supportTransform);
      combinedTerrainObject.addRotatableBox(supportTransform, supportWidth, supportLength, supportHeight, YoAppearance.AliceBlue());
   }

   private FramePose generateDebrisPose(Point3d positionWithRespectToRobot, double debrisYaw, double debrisPitch, double debrisRoll)
   {
      FramePose debrisPose = new FramePose(robotInitialPoseReferenceFrame);
      Quat4d orientation = new Quat4d();
      RotationTools.convertYawPitchRollToQuaternion(debrisYaw, debrisPitch, debrisRoll, orientation);
      debrisPose.setPose(positionWithRespectToRobot, orientation);
      debrisPose.changeFrame(constructionWorldFrame);
      return debrisPose;
   }

   private CombinedTerrainObject3D setUpGround(String name)
   {
      CombinedTerrainObject3D combinedTerrainObject = new CombinedTerrainObject3D(name);

      combinedTerrainObject.addBox(-10.0, -10.0, 10.0, 10.0, -0.05, 0.0, YoAppearance.DarkBlue());

      return combinedTerrainObject;
   }

   public void createDebrisContactController()
   {
      for (int i = 0; i < debrisRobots.size(); i++)
      {
         ContactableSelectableBoxRobot debrisRobot = debrisRobots.get(i);
         GroundContactModel groundContactModel = createGroundContactModel(debrisRobot, combinedTerrainObject);
         debrisRobot.createAvailableContactPoints(1, 20, forceVectorScale, true);
         debrisRobot.setGroundContactModel(groundContactModel);
      }
   }

   private ContactableSelectableBoxRobot createDebrisRobot(FramePose debrisPose)
   {

      debrisPose.checkReferenceFrameMatch(constructionWorldFrame);
      ContactableSelectableBoxRobot debris = ContactableSelectableBoxRobot.createContactable2By4Robot(debrisName + String.valueOf(id++), debrisDepth,
            debrisWidth, debrisLength, debrisMass);
      debris.setPosition(debrisPose.getX(), debrisPose.getY(), debrisPose.getZ());
      debris.setYawPitchRoll(debrisPose.getYaw(), debrisPose.getPitch(), debrisPose.getRoll());

      return debris;
   }

   private GroundContactModel createGroundContactModel(Robot robot, GroundProfile3D groundProfile)
   {
      double kXY = 5000.0;
      double bXY = 100.0;
      double kZ = 1000.0;
      double bZ = 100.0;
      double alphaStick = 0.7;
      double alphaSlip = 0.5;

      GroundContactModel groundContactModel = new LinearStickSlipGroundContactModel(robot, kXY, bXY, kZ, bZ, alphaSlip, alphaStick,
            robot.getRobotsYoVariableRegistry());
      groundContactModel.setGroundProfile3D(groundProfile);

      return groundContactModel;
   }

   @Override
   public TerrainObject3D getTerrainObject3D()
   {
      return combinedTerrainObject;
   }

   @Override
   public List<ContactableSelectableBoxRobot> getEnvironmentRobots()
   {
      return debrisRobots;
   }

   @Override
   public void createAndSetContactControllerToARobot()
   {
      // add contact controller to any robot so it gets called
      ContactController contactController = new ContactController("DebrisContactController");
      contactController.setContactParameters(1000.0, 100.0, 0.5, 0.3);
      contactController.addContactPoints(contactPoints);
      contactController.addContactables(debrisRobots);
      debrisRobots.get(0).setController(contactController);
   }

   @Override
   public void addContactPoints(List<? extends ExternalForcePoint> externalForcePoints)
   {
      this.contactPoints.addAll(externalForcePoints);
   }

   @Override
   public void addSelectableListenerToSelectables(SelectableObjectListener selectedListener)
   {
   }

   public double getDebrisDepth()
   {
      return debrisDepth;
   }

   public double getDebrisWidth()
   {
      return debrisWidth;
   }

   public double getDebrisLength()
   {
      return debrisLength;
   }
}