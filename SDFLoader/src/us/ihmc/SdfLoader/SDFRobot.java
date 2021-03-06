package us.ihmc.SdfLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.vecmath.Matrix3d;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.apache.commons.lang3.tuple.ImmutablePair;

import us.ihmc.SdfLoader.models.FullRobotModel;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Camera;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.IMU;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.IMU.IMUNoise;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.IMU.IMUNoise.NoiseParameters;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Ray;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Ray.Noise;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Ray.Range;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Ray.Scan;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Ray.Scan.HorizontalScan;
import us.ihmc.SdfLoader.xmlDescription.SDFSensor.Ray.Scan.VerticalScan;
import us.ihmc.graphics3DAdapter.graphics.Graphics3DObject;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.geometry.InertiaTools;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.lidar.LidarScanParameters;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.OneDoFJoint;
import us.ihmc.simulationconstructionset.CameraMount;
import us.ihmc.simulationconstructionset.DummyOneDegreeOfFreedomJoint;
import us.ihmc.simulationconstructionset.ExternalForcePoint;
import us.ihmc.simulationconstructionset.FloatingJoint;
import us.ihmc.simulationconstructionset.GroundContactPoint;
import us.ihmc.simulationconstructionset.IMUMount;
import us.ihmc.simulationconstructionset.Joint;
import us.ihmc.simulationconstructionset.JointWrenchSensor;
import us.ihmc.simulationconstructionset.Link;
import us.ihmc.simulationconstructionset.OneDegreeOfFreedomJoint;
import us.ihmc.simulationconstructionset.OneDegreeOfFreedomJointHolder;
import us.ihmc.simulationconstructionset.PinJoint;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SliderJoint;
import us.ihmc.simulationconstructionset.simulatedSensors.FeatherStoneJointBasedWrenchCalculator;
import us.ihmc.simulationconstructionset.simulatedSensors.GroundContactPointBasedWrenchCalculator;
import us.ihmc.simulationconstructionset.simulatedSensors.LidarMount;
import us.ihmc.simulationconstructionset.simulatedSensors.SimulatedLIDARSensorLimitationParameters;
import us.ihmc.simulationconstructionset.simulatedSensors.SimulatedLIDARSensorNoiseParameters;
import us.ihmc.simulationconstructionset.simulatedSensors.SimulatedLIDARSensorUpdateParameters;
import us.ihmc.simulationconstructionset.simulatedSensors.WrenchCalculatorInterface;
import us.ihmc.tools.FormattingTools;

public class SDFRobot extends Robot implements OneDegreeOfFreedomJointHolder
{

   protected static final boolean SHOW_CONTACT_POINTS = true;
   private static final boolean SHOW_COM_REFERENCE_FRAMES = false;
   private static final boolean SHOW_INERTIA_ELLIPSOIDS = false;
   private static final boolean SHOW_SENSOR_REFERENCE_FRAMES = false;
   private static final boolean DEBUG = false;
   protected final List<String> resourceDirectories;
   protected final LinkedHashMap<String, OneDegreeOfFreedomJoint> oneDoFJoints = new LinkedHashMap<String, OneDegreeOfFreedomJoint>();
   protected final FloatingJoint rootJoint;
   private final LinkedHashMap<String, SDFCamera> cameras = new LinkedHashMap<String, SDFCamera>();
   protected final LinkedHashMap<Joint, ArrayList<GroundContactPoint>> jointToGroundContactPointsMap = new LinkedHashMap<Joint, ArrayList<GroundContactPoint>>();


   private final SDFDescriptionMutator descriptionMutator;

   public SDFRobot(GeneralizedSDFRobotModel generalizedSDFRobotModel, SDFDescriptionMutator descriptionMutator, SDFJointNameMap sdfJointNameMap, boolean useCollisionMeshes,
                   boolean enableTorqueVelocityLimits, boolean enableDamping)
   {
      this(generalizedSDFRobotModel.getName(), generalizedSDFRobotModel, descriptionMutator,sdfJointNameMap,useCollisionMeshes,enableTorqueVelocityLimits,enableDamping);
   }

   public SDFRobot(String name, GeneralizedSDFRobotModel generalizedSDFRobotModel, SDFDescriptionMutator descriptionMutator, SDFJointNameMap sdfJointNameMap, boolean useCollisionMeshes,
         boolean enableTorqueVelocityLimits, boolean enableDamping)
   {
      super(name);
      this.descriptionMutator = descriptionMutator;
      this.resourceDirectories = generalizedSDFRobotModel.getResourceDirectories();
      
      ArrayList<SDFLinkHolder> rootLinks = generalizedSDFRobotModel.getRootLinks();

      if (rootLinks.size() > 1)
      {
         throw new RuntimeException("Can only accomodate one root link for now");
      }

      SDFLinkHolder rootLink = rootLinks.get(0);

      Vector3d offset = new Vector3d();
      Quat4d orientation = new Quat4d();
      generalizedSDFRobotModel.getTransformToRoot().get(orientation, offset);
      rootJoint = new FloatingJoint(rootLink.getName(), new Vector3d(), this, true);
      setPositionInWorld(offset);
      setOrientation(orientation);

      Link scsRootLink = createLink(rootLink, new RigidBodyTransform(), useCollisionMeshes);
      rootJoint.setLink(scsRootLink);
      addSensors(rootJoint, rootLink);

      addRootJoint(rootJoint);

      if (sdfJointNameMap != null)
      {
         enableTorqueVelocityLimits = enableTorqueVelocityLimits && sdfJointNameMap.isTorqueVelocityLimitsEnabled();
      }

      for (SDFJointHolder child : rootLink.getChildren())
      {
         // System.out.println("Joint name: " + child.getName());
         
         Set<String> lastSimulatedJoints;

         if (sdfJointNameMap != null)
         {
            lastSimulatedJoints = sdfJointNameMap.getLastSimulatedJoints();
         }
         else
         {
            lastSimulatedJoints = new HashSet<>();
         }
         addJointsRecursively(child, rootJoint, useCollisionMeshes, enableTorqueVelocityLimits, enableDamping, lastSimulatedJoints, false);
      }

      LinkedHashMap<String, Integer> counters = new LinkedHashMap<String, Integer>();
      if (sdfJointNameMap != null)
      {
         for (ImmutablePair<String, Vector3d> jointContactPoint : sdfJointNameMap.getJointNameGroundContactPointMap())
         {
            String jointName = jointContactPoint.getLeft();

            int count;
            if (counters.get(jointName) == null)
               count = 0;
            else
               count = counters.get(jointName);

            Vector3d gcOffset = jointContactPoint.getRight();
            
            GroundContactPoint groundContactPoint = new GroundContactPoint("gc_" + SDFConversionsHelper.sanitizeJointName(jointName) + "_" + count++, null,
                  this.getRobotsYoVariableRegistry());
            groundContactPoint.setOffsetJoint(gcOffset);

            ExternalForcePoint externalForcePoint = new ExternalForcePoint("ef_" + SDFConversionsHelper.sanitizeJointName(jointName) + "_" + count++, gcOffset,
                  this.getRobotsYoVariableRegistry());

            Joint joint;
            if (jointName.equals(rootJoint.getName()))
            {
               joint = rootJoint;
            }
            else
            {
               joint = oneDoFJoints.get(jointName);
            }

            joint.addGroundContactPoint(groundContactPoint);
            joint.addExternalForcePoint(externalForcePoint);

            counters.put(jointName, count);

//            PrintTools.info("Joint Contact Point: " + jointContactPoint);
            
            if (SHOW_CONTACT_POINTS)
            {
               Graphics3DObject graphics = joint.getLink().getLinkGraphics();
               graphics.identity();
               graphics.translate(jointContactPoint.getRight());
               double radius = 0.01;
               graphics.addSphere(radius, YoAppearance.Orange());
               
//               double bigRadius = 0.05;
//               if (jointContactPoint.first().equals("r_arm_wrx"))
//               {
//                  Graphics3DObject specialGraphic = joint.getLink().getLinkGraphics();
//                  specialGraphic.identity();
//                  specialGraphic.translate(0.00017, -0.02515, 0.00163);
//                  specialGraphic.addSphere(bigRadius, YoAppearance.Orange());
//               }
//               else if (jointContactPoint.first().equals("l_arm_wrx"))
//               {
//                  Graphics3DObject specialGraphic = joint.getLink().getLinkGraphics();
//                  specialGraphic.identity();
//                  specialGraphic.translate(0.00017, 0.02515, 0.00163);
//                  specialGraphic.addSphere(bigRadius, YoAppearance.Orange());
//               }
            }

            if (!jointToGroundContactPointsMap.containsKey(joint)) 
            {
               jointToGroundContactPointsMap.put(joint, new ArrayList<GroundContactPoint>());
            }
            jointToGroundContactPointsMap.get(joint).add(groundContactPoint);
//            
//            
//            for (RobotSide robotSide : RobotSide.values)
//            {
//               
//               
//               
//               if (jointName.equals(sdfJointNameMap.getJointBeforeFootName(robotSide)))
//               {
//                  footGroundContactPoints.get(robotSide).add(groundContactPoint);
//               }
//               else if(jointName.equals(sdfJointNameMap.getJointBeforeHandName(robotSide)))
//               {
//                  handGroundContactPoints.get(robotSide).add(groundContactPoint);
//               }
//            }
         }
      }

      for (SDFJointHolder child : rootLink.getChildren())
      {
         addForceSensorsIncludingDescendants(child, sdfJointNameMap);
      }

      Point3d centerOfMass = new Point3d();
      double totalMass = computeCenterOfMass(centerOfMass);
      printIfDebug("SDFRobot: Total robot mass: " + FormattingTools.getFormattedDecimal3D(totalMass) + " (kg)");
   }

   public Quat4d getRootJointToWorldRotationQuaternion()
   {
      return rootJoint.getQuaternion();
   }

   public void getRootJointToWorldTransform(RigidBodyTransform transform)
   {
      rootJoint.getTransformToWorld(transform);
   }

   public void setPositionInWorld(Tuple3d offset)
   {
      rootJoint.setPosition(offset);
   }

   public void setOrientation(double yaw, double pitch, double roll)
   {
      rootJoint.setYawPitchRoll(yaw, pitch, roll);
   }

   public void setOrientation(Quat4d quaternion)
   {
      rootJoint.setQuaternion(quaternion);
   }

   public void setAngularVelocity(Vector3d velocity)
   {
      rootJoint.setAngularVelocityInBody(velocity);
   }

   public void setLinearVelocity(Vector3d velocity)
   {
      rootJoint.setVelocity(velocity);
   }

   @Override
   public OneDegreeOfFreedomJoint getOneDegreeOfFreedomJoint(String name)
   {
      return oneDoFJoints.get(name);
   }

   public FloatingJoint getRootJoint()
   {
      return rootJoint;
   }

   public OneDegreeOfFreedomJoint[] getOneDoFJoints()
   {
      return oneDoFJoints.values().toArray(new OneDegreeOfFreedomJoint[oneDoFJoints.size()]);
   }

   protected void addForceSensorsIncludingDescendants(SDFJointHolder joint, SDFJointNameMap jointNameMap)
   {
      if(jointNameMap != null)
      {
         addForceSensor(joint, jointNameMap);         
      }
   
      for (SDFJointHolder child : joint.getChildLinkHolder().getChildren())
      {
         addForceSensorsIncludingDescendants(child, jointNameMap);
      }
   }

   private void addForceSensor(SDFJointHolder joint, SDFJointNameMap jointNameMap)
   {
//         String jointBeforeLeftFootName = jointNameMap.getJointBeforeFootName(RobotSide.LEFT);
//         String jointBeforeRightFootName = jointNameMap.getJointBeforeFootName(RobotSide.RIGHT);
         String[] jointNamesBeforeFeet = jointNameMap.getJointNamesBeforeFeet();
         if (joint.getForceSensors().size() > 0)
         {
            String jointName = joint.getName();
            String sanitizedJointName = SDFConversionsHelper.sanitizeJointName(jointName);
            OneDegreeOfFreedomJoint scsJoint = getOneDegreeOfFreedomJoint(sanitizedJointName);
   
            for (SDFForceSensor forceSensor : joint.getForceSensors())
            {
               ArrayList<GroundContactPoint> groundContactPoints = new ArrayList<GroundContactPoint>();
               scsJoint.physics.recursiveGetAllGroundContactPoints(groundContactPoints);
   
               WrenchCalculatorInterface wrenchCalculator;
               
               boolean jointIsParentOfFoot = false;
               for(int i = 0; i < jointNamesBeforeFeet.length; i++)
               {
                  if(jointName.equals(jointNamesBeforeFeet[i]))
                  {
                     jointIsParentOfFoot = true;
                  }
               }
   
               if ( jointIsParentOfFoot )
               {
   //               System.out.println("SDFRobot: Adding old-school force sensor to: " + joint.getName());
                  wrenchCalculator = new GroundContactPointBasedWrenchCalculator(
                        forceSensor.getName(), groundContactPoints, scsJoint, forceSensor.getTransform());
               }
               else
               {
   //               System.out.println("SDFRobot: Adding force sensor to: " + joint.getName());
                  
                  Vector3d offsetToPack = new Vector3d();
                  forceSensor.getTransform().getTranslation(offsetToPack);
                  JointWrenchSensor jointWrenchSensor = new JointWrenchSensor(jointName, offsetToPack, this);
                  scsJoint.addJointWrenchSensor(jointWrenchSensor);
                  
                  wrenchCalculator = new FeatherStoneJointBasedWrenchCalculator(forceSensor.getName(), scsJoint);
               }
               scsJoint.addForceSensor(wrenchCalculator);
   
            }
         }
      }

   protected void addJointsRecursively(SDFJointHolder joint, Joint scsParentJoint, boolean useCollisionMeshes, boolean enableTorqueVelocityLimits, boolean enableDamping, Set<String> lastSimulatedJoints, boolean asNullJoint)
   {
         Vector3d jointAxis = new Vector3d(joint.getAxisInModelFrame());
         Vector3d offset = new Vector3d(joint.getOffsetFromParentJoint());
   
         RigidBodyTransform visualTransform = new RigidBodyTransform();
         visualTransform.setRotation(joint.getLinkRotation());
   
         String sanitizedJointName = SDFConversionsHelper.sanitizeJointName(joint.getName());
   
         Joint scsJoint;
   
         if (asNullJoint)
         {
            DummyOneDegreeOfFreedomJoint dummyJoint = new DummyOneDegreeOfFreedomJoint(sanitizedJointName, offset, this, jointAxis);
            oneDoFJoints.put(joint.getName(), dummyJoint);
            scsJoint = dummyJoint;
         }
         else
         {
            switch (joint.getType())
            {
            case REVOLUTE:
               PinJoint pinJoint = new PinJoint(sanitizedJointName, offset, this, jointAxis);
               if (joint.hasLimits())
               {
                  if (isJointInNeedOfReducedGains(joint))
                  {
                     pinJoint.setLimitStops(joint.getLowerLimit(), joint.getUpperLimit(), 10.0, 2.5);
                  }
                  else
                  {
                     if ((joint.getContactKd() == 0.0) && (joint.getContactKp() == 0.0))
                     {
                        pinJoint.setLimitStops(joint.getLowerLimit(), joint.getUpperLimit(), 100.0, 20.0);
   //                     pinJoint.setLimitStops(joint.getLowerLimit(), joint.getUpperLimit(), 1000.0, 200.0);
                     }
                     else
                     {
                        pinJoint.setLimitStops(joint.getLowerLimit(), joint.getUpperLimit(), 0.0001 * joint.getContactKp(), 0.1 * joint.getContactKd());
                     }
                     
                     if (!Double.isNaN(joint.getVelocityLimit()))
                        pinJoint.setVelocityLimits(joint.getVelocityLimit(), 0.0);
                     //System.out.println("SDFRobot: joint.getVelocityLimit()=" + joint.getVelocityLimit());
                     
                  }
               }
   
               if (enableDamping)
               {
                  pinJoint.setDamping(joint.getDamping());
                  pinJoint.setStiction(joint.getFriction());
               }
               else
               {
                  pinJoint.setDampingParameterOnly(joint.getDamping());
                  pinJoint.setStictionParameterOnly(joint.getFriction());
               }
   
               if (enableTorqueVelocityLimits)
               {
                  if (!isJointInNeedOfReducedGains(joint))
                  {
                     if (!Double.isNaN(joint.getEffortLimit()))
                     {
                        pinJoint.setTorqueLimits(joint.getEffortLimit());
                     }
   
                     if (!Double.isNaN(joint.getVelocityLimit()))
                     {
                        if (!isJointInNeedOfReducedGains(joint))
                        {
                           pinJoint.setVelocityLimits(joint.getVelocityLimit(), 500.0);
                        }
                     }
                  }
               }
   
               oneDoFJoints.put(joint.getName(), pinJoint);
               scsJoint = pinJoint;
   
               break;
   
            case PRISMATIC:
               SliderJoint sliderJoint = new SliderJoint(sanitizedJointName, offset, this, jointAxis);
               if (joint.hasLimits())
               {
                  if ((joint.getContactKd() == 0.0) && (joint.getContactKp() == 0.0))
                  {
                     sliderJoint.setLimitStops(joint.getLowerLimit(), joint.getUpperLimit(), 100.0, 20.0);
                  }
                  else
                  {
                     sliderJoint.setLimitStops(joint.getLowerLimit(), joint.getUpperLimit(), 0.0001 * joint.getContactKp(), joint.getContactKd());
                  }
               }
   
               if (enableDamping)
               {
                  sliderJoint.setDamping(joint.getDamping());
               }
               else
               {
                  sliderJoint.setDampingParameterOnly(joint.getDamping());
               }
   
               oneDoFJoints.put(joint.getName(), sliderJoint);
   
               scsJoint = sliderJoint;
   
               break;
   
            default:
               throw new RuntimeException("Joint type not implemented: " + joint.getType());
            }
         }
   
         scsJoint.setLink(createLink(joint.getChildLinkHolder(), visualTransform, useCollisionMeshes));
         scsParentJoint.addJoint(scsJoint);
   
         addSensors(scsJoint, joint.getChildLinkHolder());
   
         if (!asNullJoint && lastSimulatedJoints.contains(joint.getName()))
         {
            asNullJoint = true;
         }
   
         for (SDFJointHolder child : joint.getChildLinkHolder().getChildren())
         {
            addJointsRecursively(child, scsJoint, useCollisionMeshes, enableTorqueVelocityLimits, enableDamping, lastSimulatedJoints, asNullJoint);
         }
   
      }

   ///TODO:
   ///XXX: pull these names from the sdfJointNameMap
   private boolean isJointInNeedOfReducedGains(SDFJointHolder pinJoint)
   {
      String jointName = pinJoint.getName();
      return jointName.contains("f0") || jointName.contains("f1") || jointName.contains("f2") || jointName.contains("f3") || jointName.contains("palm")
            || jointName.contains("finger");
   }

   protected void addSensors(Joint scsJoint, SDFLinkHolder child)
   {
      if (child.getSensors() != null)
      {
         for (SDFSensor sensor : child.getSensors())
         {
            switch (sensor.getType())
            {
            case "camera":
            case "multicamera":
               addCameraMounts(sensor, scsJoint, child);
               break;
            case "imu":
               addIMUMounts(sensor, scsJoint, child);
               break;
            case "gpu_ray":
            case "ray":
               addLidarMounts(sensor, scsJoint, child);
               break;
            }
         }
      }
   }

   private void showCordinateSystem(Joint scsJoint, RigidBodyTransform offsetFromLink)
   {
      if (SHOW_SENSOR_REFERENCE_FRAMES)
      {
         Graphics3DObject linkGraphics = scsJoint.getLink().getLinkGraphics();
         linkGraphics.identity();
         linkGraphics.transform(offsetFromLink);
         linkGraphics.addCoordinateSystem(1.0);
         linkGraphics.identity();
      }
   }

   private void addCameraMounts(SDFSensor sensor, Joint scsJoint, SDFLinkHolder child)
   {
      // TODO: handle left and right sides of multicamera
      final List<Camera> cameras = sensor.getCamera();
   
      if (cameras != null)
      {
         for (Camera camera : cameras)
         {
            // The linkRotation transform is to make sure that the linkToSensor is in a zUpFrame.
            RigidBodyTransform linkRotation = new RigidBodyTransform(child.getTransformFromModelReferenceFrame());
            linkRotation.setTranslation(0.0, 0.0, 0.0);
            RigidBodyTransform linkToSensor = SDFConversionsHelper.poseToTransform(sensor.getPose());
            RigidBodyTransform sensorToCamera = SDFConversionsHelper.poseToTransform(camera.getPose());
            RigidBodyTransform linkToCamera = new RigidBodyTransform();
            linkToCamera.multiply(linkRotation, linkToSensor);
            linkToCamera.multiply(sensorToCamera);
            showCordinateSystem(scsJoint, linkToCamera);
   
            double fieldOfView = Double.parseDouble(camera.getHorizontalFov());
            double clipNear = Double.parseDouble(camera.getClip().getNear());
            double clipFar = Double.parseDouble(camera.getClip().getFar());
            String cameraName = sensor.getName() + "_" + camera.getName();
            CameraMount mount = new CameraMount(cameraName, linkToCamera, fieldOfView, clipNear, clipFar, this);
            scsJoint.addCameraMount(mount);
   
            SDFCamera sdfCamera = new SDFCamera(Integer.parseInt(camera.getImage().getWidth()), Integer.parseInt(camera.getImage().getHeight()));
            this.cameras.put(cameraName, sdfCamera);
         }
      }
      else
      {
         System.err.println("JAXB loader: No camera section defined for camera sensor " + sensor.getName() + ", ignoring sensor.");
      }
   }

   private void addIMUMounts(SDFSensor sensor, Joint scsJoint, SDFLinkHolder child)
   {
      // TODO: handle left and right sides of multicamera
      final IMU imu = sensor.getImu();
   
      if (imu != null)
      {
         // The linkRotation transform is to make sure that the linkToSensor is in a zUpFrame.
         RigidBodyTransform linkRotation = new RigidBodyTransform(child.getTransformFromModelReferenceFrame());
         linkRotation.setTranslation(0.0, 0.0, 0.0);
         RigidBodyTransform linkToSensorInZUp = new RigidBodyTransform();
         linkToSensorInZUp.multiply(linkRotation, SDFConversionsHelper.poseToTransform(sensor.getPose()));
   
         showCordinateSystem(scsJoint, linkToSensorInZUp);
         IMUMount imuMount = new IMUMount(child.getName() + "_" + sensor.getName(), linkToSensorInZUp, this);
   
         IMUNoise noise = imu.getNoise();
         if (noise != null)
         {
            if ("gaussian".equals(noise.getType()))
            {
               NoiseParameters accelerationNoise = noise.getAccel();
               NoiseParameters angularVelocityNoise = noise.getRate();
   
               imuMount.setAccelerationNoiseParameters(Double.parseDouble(accelerationNoise.getMean()), Double.parseDouble(accelerationNoise.getStddev()));
               imuMount.setAccelerationBiasParameters(Double.parseDouble(accelerationNoise.getBias_mean()),
                     Double.parseDouble(accelerationNoise.getBias_stddev()));
   
               imuMount.setAngularVelocityNoiseParameters(Double.parseDouble(angularVelocityNoise.getMean()),
                     Double.parseDouble(angularVelocityNoise.getStddev()));
               imuMount.setAngularVelocityBiasParameters(Double.parseDouble(angularVelocityNoise.getBias_mean()),
                     Double.parseDouble(angularVelocityNoise.getBias_stddev()));
   
            }
            else
            {
               throw new RuntimeException("Unknown IMU noise model: " + noise.getType());
            }
         }
   
         scsJoint.addIMUMount(imuMount);
   
      }
      else
      {
         System.err.println("JAXB loader: No imu section defined for imu sensor " + sensor.getName() + ", ignoring sensor.");
      }
   }

   private void addLidarMounts(SDFSensor sensor, Joint scsJoint, SDFLinkHolder child)
   {
      Ray sdfRay = sensor.getRay();
      if (sdfRay == null)
      {
         System.err.println("SDFRobot: lidar not present in ray type sensor " + sensor.getName() + ". Ignoring this sensor.");
      }
      else
      {
         Range sdfRange = sdfRay.getRange();
         Scan sdfScan = sdfRay.getScan();
         double sdfMaxRange = Double.parseDouble(sdfRange.getMax());
         double sdfMinRange = Double.parseDouble(sdfRange.getMin());
         HorizontalScan sdfHorizontalScan = sdfScan.getHorizontal();
         VerticalScan sdfVerticalScan = sdfScan.getVertical();
         double sdfMaxSweepAngle = Double.parseDouble(sdfHorizontalScan.getMaxAngle());
         double sdfMinSweepAngle = Double.parseDouble(sdfHorizontalScan.getMinAngle());
         double sdfMaxHeightAngle = sdfVerticalScan == null ? 0.0 : Double.parseDouble(sdfVerticalScan.getMaxAngle());
         double sdfMinHeightAngle = sdfVerticalScan == null ? 0.0 : Double.parseDouble(sdfVerticalScan.getMinAngle());
   
         // double sdfAngularResolution = Double.parseDouble(sdfHorizontalScan.getSillyAndProbablyNotUsefulResolution());
         int sdfSamples = (Integer.parseInt(sdfHorizontalScan.getSamples()) / 3) * 3;
         int sdfScanHeight = sdfVerticalScan == null ? 1 : Integer.parseInt(sdfVerticalScan.getSamples());
         double sdfRangeResolution = Double.parseDouble(sdfRay.getRange().getResolution());
   
         boolean sdfAlwaysOn = true;
   
         double sdfGaussianStdDev = 0.0;
         double sdfGaussianMean = 0.0;
         int sdfUpdateRate = (int) (1000.0 / Double.parseDouble(sensor.getUpdateRate()));
   
         Noise sdfNoise = sdfRay.getNoise();
         if (sdfNoise != null)
         {
            if ("gaussian".equals(sdfNoise.getType()))
            {
               sdfGaussianStdDev = Double.parseDouble(sdfNoise.getStddev());
               sdfGaussianMean = Double.parseDouble(sdfNoise.getMean());
            }
            else
            {
               System.err.println("Unknown noise model: " + sdfNoise.getType());
            }
         }
   
         //         System.err.println("[SDFRobot]: FIXME: Setting LIDAR angle to 0.5 pi due to current GPULidar limitations");
         //         sdfMinAngle = -Math.PI/4;
         //         sdfMaxAngle = Math.PI/4;
   
         LidarScanParameters polarDefinition = new LidarScanParameters(sdfSamples, sdfScanHeight, (float) sdfMinSweepAngle, (float) sdfMaxSweepAngle, (float) sdfMinHeightAngle, (float) sdfMaxHeightAngle, 0.0f, (float) sdfMinRange,
               (float) sdfMaxRange, 0.0f, 0l);
   
         // The linkRotation transform is to make sure that the linkToSensor is in a zUpFrame.
         RigidBodyTransform linkRotation = new RigidBodyTransform(child.getTransformFromModelReferenceFrame());
         linkRotation.setTranslation(0.0, 0.0, 0.0);
         RigidBodyTransform linkToSensorInZUp = new RigidBodyTransform();
         linkToSensorInZUp.multiply(linkRotation, SDFConversionsHelper.poseToTransform(sensor.getPose()));
         showCordinateSystem(scsJoint, linkToSensorInZUp);
   
         SimulatedLIDARSensorNoiseParameters noiseParameters = new SimulatedLIDARSensorNoiseParameters();
         noiseParameters.setGaussianNoiseStandardDeviation(sdfGaussianStdDev);
         noiseParameters.setGaussianNoiseMean(sdfGaussianMean);
   
         SimulatedLIDARSensorLimitationParameters limitationParameters = new SimulatedLIDARSensorLimitationParameters();
         limitationParameters.setMaxRange(sdfMaxRange);
         limitationParameters.setMinRange(sdfMinRange);
         limitationParameters.setQuantization(sdfRangeResolution);
   
         SimulatedLIDARSensorUpdateParameters updateParameters = new SimulatedLIDARSensorUpdateParameters();
         updateParameters.setAlwaysOn(sdfAlwaysOn);
         updateParameters.setUpdatePeriodInMillis(sdfUpdateRate);
   
         LidarMount lidarMount = new LidarMount(linkToSensorInZUp, polarDefinition, sensor.getName());
         scsJoint.addSensor(lidarMount);
   
      }
   }

   protected Link createLink(SDFLinkHolder link, RigidBodyTransform rotationTransform, boolean useCollisionMeshes)
   {
      Link scsLink = new Link(link.getName());
      if (useCollisionMeshes)
      {
         SDFGraphics3DObject linkGraphics = new SDFGraphics3DObject(link.getCollisions(), resourceDirectories, rotationTransform);
         scsLink.setLinkGraphics(linkGraphics);
      }
      else if (link.getVisuals() != null)
      {
         SDFGraphics3DObject linkGraphics = new SDFGraphics3DObject(link.getVisuals(), resourceDirectories, rotationTransform);
         scsLink.setLinkGraphics(linkGraphics);
      }
   
      double mass = link.getMass();
      Matrix3d inertia = InertiaTools.rotate(rotationTransform, link.getInertia());
      Vector3d CoMOffset = new Vector3d(link.getCoMOffset());
   
      if (link.getJoint() != null)
      {
         if (isJointInNeedOfReducedGains(link.getJoint()))
         {
            inertia.mul(100.0);
         }
      }
   
      rotationTransform.transform(CoMOffset);
   
      scsLink.setComOffset(CoMOffset);
      scsLink.setMass(mass);
      scsLink.setMomentOfInertia(inertia);
   
      if (SHOW_COM_REFERENCE_FRAMES)
      {
         scsLink.addCoordinateSystemToCOM(0.1);
      }
      if (SHOW_INERTIA_ELLIPSOIDS)
      {
         scsLink.addEllipsoidFromMassProperties(YoAppearance.Orange());
      }
   
      return scsLink;
   
   }

   public FrameVector getRootJointVelocity()
   {
      FrameVector ret = new FrameVector(ReferenceFrame.getWorldFrame());
      rootJoint.getVelocity(ret.getVector());
   
      return ret;
   }

   public FrameVector getPelvisAngularVelocityInPelvisFrame(ReferenceFrame pelvisFrame)
   {
      Vector3d angularVelocity = rootJoint.getAngularVelocityInBody();
   
      return new FrameVector(pelvisFrame, angularVelocity);
   }

   public Graphics3DObject getGraphicsObject(String name)
   {
      if (rootJoint.getName().equals(name))
      {
         return rootJoint.getLink().getLinkGraphics();
      }
   
      return oneDoFJoints.get(name).getLink().getLinkGraphics();
   }

   public SDFCamera getCamera(String name)
   {
      return cameras.get(name);
   }

   public Vector3d getPositionInWorld()
   {
      Vector3d position = new Vector3d();
      getPositionInWorld(position);
   
      return position;
   }

   public void getPositionInWorld(Vector3d vectorToPack)
   {
      rootJoint.getPosition(vectorToPack);
   }

   public void getVelocityInWorld(Vector3d vectorToPack)
   {
      rootJoint.getVelocity(vectorToPack);
   }

   public void getOrientationInWorld(Quat4d quaternionToPack)
   {
      rootJoint.getQuaternion(quaternionToPack);
   }

   public void getAngularVelocityInBody(Vector3d vectorToPack)
   {
      rootJoint.getAngularVelocityInBody(vectorToPack);
   }

   public Joint getJoint(String jointName)
   {
      return getJointRecursively(this.getRootJoints(), jointName);
   }

   private Joint getJointRecursively(ArrayList<Joint> joints, String jointName)
   {
      for (Joint joint : joints)
      {
         String nextJointName = joint.getName();
   
         // System.out.println(nextJointName);
         if (nextJointName.equals(jointName))
         {
            return joint;
         }
   
         ArrayList<Joint> children = joint.getChildrenJoints();
   
         Joint foundJoint = getJointRecursively(children, jointName);
         if (foundJoint != null)
            return foundJoint;
      }
   
      return null;
   }

   public FloatingJoint getPelvisJoint()
   {
      return getRootJoint();
   }

   public void copyWholeBodyStateFromFullRobotModel(FullRobotModel modelToCopyFrom, boolean copyRootJointToo)
   {
      OneDoFJoint[] oneDoFJoints = modelToCopyFrom.getOneDoFJoints();
      for (OneDoFJoint otherJoint: oneDoFJoints)
      {
         OneDegreeOfFreedomJoint thisJoint = this.getOneDegreeOfFreedomJoint( otherJoint.getName() );
         if( thisJoint != null)
         {
            thisJoint.setQ( otherJoint.getQ() );
            thisJoint.setQd( otherJoint.getQd() );
            thisJoint.setQdd( otherJoint.getQdd() );
         }
      }
   
      if( copyRootJointToo )
      {
         Vector3d position = new Vector3d();
         Quat4d rotation = new Quat4d();
         modelToCopyFrom.getRootJoint().getFrameAfterJoint().getTransformToWorldFrame().get(rotation, position);
         this.setPositionInWorld( position );
         this.setOrientation(rotation);
      }
   }

   protected void printIfDebug(String string)
   {
      if (DEBUG) System.out.println(string);
   }

}