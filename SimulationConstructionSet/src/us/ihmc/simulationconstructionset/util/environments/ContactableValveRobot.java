package us.ihmc.simulationconstructionset.util.environments;

import java.util.ArrayList;

import javax.vecmath.Matrix3d;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import us.ihmc.graphics3DAdapter.graphics.Graphics3DObject;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.graphics3DAdapter.input.SelectedListener;
import us.ihmc.graphics3DAdapter.structure.Graphics3DNode;
import us.ihmc.simulationconstructionset.Link;
import us.ihmc.simulationconstructionset.PinJoint;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.tools.inputDevices.keyboard.ModifierKeyInterface;
import us.ihmc.robotics.geometry.RotationalInertiaCalculator;
import us.ihmc.robotics.geometry.shapes.FrameCylinder3d;
import us.ihmc.robotics.dataStructures.listener.VariableChangedListener;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.YoVariable;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.geometry.shapes.FrameTorus3d;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.geometry.RotationTools;

public class ContactableValveRobot extends ContactablePinJointRobot implements SelectableObject, SelectedListener
{
   private static final double DEFAULT_DAMPING = 3;

   private String name;

   protected double valveRadius;
   private double valveOffsetFromWall;
   private double valveThickness;

   private int numberOfSpokes;
   protected double spokesThickness;

   private FramePose valvePoseInWorld = new FramePose();

   private double valveNumberOfPossibleTurns;

   private final DoubleYoVariable valveClosePercentage;
   
   private double valveMass;
   private Matrix3d inertiaMatrix;

   private FrameTorus3d valveTorus;
   protected ArrayList<FrameCylinder3d> spokesCylinders = new ArrayList<FrameCylinder3d>();

   protected Link valveLink;
   private PinJoint valvePinJoint;
   protected Graphics3DObject valveLinkGraphics = new Graphics3DObject();
   private final DoubleYoVariable valveDamping;

   protected PoseReferenceFrame valveFrame;

   private final RigidBodyTransform originalValvePose = new RigidBodyTransform();

   public ContactableValveRobot(String name, double valveRadius, double valveOffsetFromWall, double valveThickness, int numberOfSpokes, double spokesThickness,
         FramePose valvePoseInWorld, double valveNumberOfPossibleTurns, double valveMass)
   {
      super(name);
      this.name = name;
      setValveProperties(valveRadius, valveOffsetFromWall, valveThickness, numberOfSpokes, spokesThickness, valveNumberOfPossibleTurns, valveMass);
      setPoseInWorld(valvePoseInWorld);
      setMass(valveMass);
      valveDamping = new DoubleYoVariable(getName() + "ValveDamping", yoVariableRegistry);
      valveDamping.set(DEFAULT_DAMPING);
      valveClosePercentage = new DoubleYoVariable("valveClosePercentage", yoVariableRegistry);
      valveClosePercentage.set(0.0);
   }

   public ContactableValveRobot(String name, double valveRadius, double valveOffsetFromWall, double valveThickness, int numberOfSpokes, double spokesThickness,
         Point3d valvePosition, Quat4d valveOrientation, double valveNumberOfPossibleTurns, double valveMass)
   {
      this(name, valveRadius, valveOffsetFromWall, valveThickness, numberOfSpokes, spokesThickness, new FramePose(ReferenceFrame.getWorldFrame(),
            valvePosition, valveOrientation), valveNumberOfPossibleTurns, valveMass);
   }

   public ContactableValveRobot(String name, ValveType valveType, double valveNumberOfPossibleTurns, FramePose valvePoseInWorld)
   {
      this(name, valveType.getValveRadius(), valveType.getValveOffsetFromWall(), valveType.getValveThickness(), valveType.getNumberOfSpokes(), valveType
            .getSpokesThickness(), valvePoseInWorld, valveNumberOfPossibleTurns, valveType.getValveMass());
   }

   public void setValveProperties(double valveRadius, double valveOffsetFromWall, double valveThickness, int numberOfSpokes, double spokesThickness,
         double valveNumberOfPossibleTurns, double valveMass)
   {
      this.valveRadius = valveRadius;
      this.valveOffsetFromWall = valveOffsetFromWall;
      this.valveThickness = valveThickness;

      this.numberOfSpokes = numberOfSpokes;
      this.spokesThickness = spokesThickness;

      this.valveNumberOfPossibleTurns = valveNumberOfPossibleTurns;

      this.valveMass = valveMass;
   }

   public void createValveRobot()
   {
      YoGraphicsListRegistry graphListRegistry = new YoGraphicsListRegistry();

      valveFrame = new PoseReferenceFrame("valveFrame", valvePoseInWorld);
      valveFrame.getPose(originalValvePose);

      //creating the pinJoint 
      Vector3d jointAxisVector = new Vector3d(1.0, 0.0, 0.0);
      RigidBodyTransform valveTransformToWorld = new RigidBodyTransform();
      valveFrame.getTransformToDesiredFrame(valveTransformToWorld, ReferenceFrame.getWorldFrame());
      valveTransformToWorld.transform(jointAxisVector);
            
      Vector3d valvePositionInWorld = new Vector3d();
      valvePoseInWorld.getPosition(valvePositionInWorld);
      valvePinJoint = new PinJoint("valvePinJoint", valvePositionInWorld, this, jointAxisVector);
      valvePinJoint.setLimitStops(0.0, valveNumberOfPossibleTurns * 2 * Math.PI, 1000, 100);
      valvePinJoint.setDamping(valveDamping.getDoubleValue());
      
      //put the graphics frame in the proper orientation
      Matrix3d rotationMatrix = new Matrix3d();
      valvePoseInWorld.getOrientation(rotationMatrix);
      valveLinkGraphics.rotate(rotationMatrix);
      RigidBodyTransform rotationTransform = new RigidBodyTransform();
      rotationTransform.setRotation(rotationMatrix);

      //Creating the physical link for the simulation
      valveLink = new Link("valveLink");
      valveLink.setMass(valveMass);
      valveLink.setComOffset(new Vector3d(0.0, 0.0, 0.0));

      inertiaMatrix = RotationalInertiaCalculator.getRotationalInertiaMatrixOfTorus(valveMass, valveRadius, valveThickness);
      valveLink.setMomentOfInertia(inertiaMatrix);
      valvePinJoint.setLink(valveLink);
      this.addRootJoint(valvePinJoint);
      
      //torus and offsetCylinder
      RigidBodyTransform transform = new RigidBodyTransform();
      RigidBodyTransform invertTransform = new RigidBodyTransform();
      Quat4d quat = new Quat4d();

      RotationTools.convertYawPitchRollToQuaternion(0.0, Math.PI / 2.0, 0.0, quat);
      transform.setRotation(quat);
      invertTransform.set(transform);
      invertTransform.invert();

      valveTorus = new FrameTorus3d(valveFrame, transform, valveRadius - valveThickness / 2.0, valveThickness / 2.0);
      valveLinkGraphics.transform(transform);
      valveLinkGraphics.addArcTorus(0.0, 2 * Math.PI, valveRadius - valveThickness / 2.0, valveThickness / 2.0, YoAppearance.DarkRed());
      valveLinkGraphics.addCylinder(valveOffsetFromWall, spokesThickness / 2.0, YoAppearance.DarkRed());
      valveLinkGraphics.transform(invertTransform);

      //spokes
      for (int i = 0; i < numberOfSpokes; i++)
      {
         RotationTools.convertYawPitchRollToQuaternion(0.0, 0.0, i * 2.0 * Math.PI / numberOfSpokes, quat);
         transform.setRotation(quat);
         invertTransform.set(transform);
         invertTransform.invert();

         RigidBodyTransform yoGraphicTransform = new RigidBodyTransform();
         yoGraphicTransform.multiply(rotationTransform, transform);

         FrameCylinder3d spokeCylinder = new FrameCylinder3d(valveFrame, transform, valveRadius - spokesThickness / 2.0, spokesThickness / 2.0);
         spokesCylinders.add(spokeCylinder);

         valveLinkGraphics.transform(transform);
         valveLinkGraphics.addCylinder(valveRadius - spokesThickness / 2.0, spokesThickness / 2.0, YoAppearance.DarkRed());
         valveLinkGraphics.transform(invertTransform);
      }

      //setting the graphics for the link
      valveLink.setLinkGraphics(valveLinkGraphics);

      yoGraphicsListRegistries.add(graphListRegistry);
      
      valvePinJoint.getQ().addVariableChangedListener(new VariableChangedListener()
      {
         
         @Override
         public void variableChanged(YoVariable<?> v)
         {
            valveClosePercentage.set(valvePinJoint.getQ().getDoubleValue()/(2*Math.PI)*100/valveNumberOfPossibleTurns);
         }
      });
   }

   @Override
   public void updateAllGroundContactPointVelocities()
   {
      RigidBodyTransform pinJointTransform = new RigidBodyTransform();
      RigidBodyTransform newValvePose = new RigidBodyTransform();
      pinJointTransform.setRotationRollAndZeroTranslation(valvePinJoint.getQ().getDoubleValue());
      newValvePose.multiply(originalValvePose, pinJointTransform);
      valveFrame.setPoseAndUpdate(newValvePose);

      super.updateAllGroundContactPointVelocities();
   }

   private final FramePoint pointToCheck = new FramePoint();
   @Override
   public boolean isPointOnOrInside(Point3d pointInWorldToCheck)
   {
      pointToCheck.setIncludingFrame(ReferenceFrame.getWorldFrame(), pointInWorldToCheck);
      pointToCheck.changeFrame(valveFrame);

      if (valveTorus.isInsideOrOnSurface(pointToCheck))
         return true;
      for (int i = 0; i < spokesCylinders.size(); i++)
      {
         if (spokesCylinders.get(i).isInsideOrOnSurface(pointToCheck))
            return true;
      }
      return false;
   }

   @Override
   public boolean isClose(Point3d pointInWorldToCheck)
   {
      return isPointOnOrInside(pointInWorldToCheck);
   }

   @Override
   public void closestIntersectionAndNormalAt(Point3d intersectionToPack, Vector3d normalToPack, Point3d pointInWorldToCheck)
   {
      FramePoint pointToCheck = new FramePoint(ReferenceFrame.getWorldFrame(), pointInWorldToCheck);
      pointToCheck.changeFrame(valveFrame);

      if (valveTorus.checkIfInside(pointToCheck, intersectionToPack, normalToPack))
         return;
      for (int i = 0; i < spokesCylinders.size(); i++)
      {
         if (spokesCylinders.get(i).checkIfInside(pointToCheck, intersectionToPack, normalToPack))
            return;
      }
   }

   @Override
   public void selected(Graphics3DNode graphics3dNode, ModifierKeyInterface modifierKeyInterface, Point3d location, Point3d cameraLocation,
         Quat4d cameraRotation)
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void select()
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void unSelect(boolean reset)
   {
      // TODO Auto-generated method stub

   }

   @Override
   public void addSelectedListeners(SelectableObjectListener selectedListener)
   {
      // TODO Auto-generated method stub

   }
   
   @Override
   public PinJoint getPinJoint()
   {
      return valvePinJoint;
   }
   
   @Override
   public void getBodyTransformToWorld(RigidBodyTransform transformToWorld)
   {
      transformToWorld.set(originalValvePose);
   }

   public double getValveRadius()
   {
      return valveRadius;
   }
   
   public double getClosePercentage()
   {
      return valveClosePercentage.getDoubleValue();
   }
   
   public double getNumberOfPossibleTurns()
   {
      return valveNumberOfPossibleTurns;
   }
   
   @Override
   public void setMass(double mass)
   {
      this.valveMass = mass;
   }

   @Override
   public void setMomentOfInertia(double Ixx, double Iyy, double Izz)
   {
      inertiaMatrix.setM00(Ixx);
      inertiaMatrix.setM01(0.0);
      inertiaMatrix.setM02(0.0);
      inertiaMatrix.setM10(0.0);
      inertiaMatrix.setM11(Iyy);
      inertiaMatrix.setM12(0.0);
      inertiaMatrix.setM20(0.0);
      inertiaMatrix.setM21(0.0);
      inertiaMatrix.setM22(Izz);
   }

   public void setPoseInWorld(FramePose valvePoseInWorld)
   {
      this.valvePoseInWorld.setPose(valvePoseInWorld);
   }

   public void setPoseInWorld(Point3d position, Quat4d orientation)
   {
      this.valvePoseInWorld.setPose(position, orientation);
   }

   public void setDamping(double dampingValue)
   {
      valveDamping.set(dampingValue);
   }

   public void setClosePercentage(double percentage)
   {
      valveClosePercentage.set(percentage);
      valvePinJoint.setQ(valveNumberOfPossibleTurns* 2 * Math.PI * percentage/100 );
   }
}
