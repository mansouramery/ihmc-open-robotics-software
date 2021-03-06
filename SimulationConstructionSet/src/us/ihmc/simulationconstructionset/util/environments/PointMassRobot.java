package us.ihmc.simulationconstructionset.util.environments;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import us.ihmc.graphics3DAdapter.graphics.Graphics3DObject;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.simulationconstructionset.ExternalForcePoint;
import us.ihmc.simulationconstructionset.Link;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SliderJoint;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicPosition;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicVector;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.robotics.Axis;

public class PointMassRobot extends Robot
{
   private static final long serialVersionUID = 471055183219410116L;

   private static final double DEFAULT_MASS = 10.0;
   private static final double DEFAULT_RADIUS = 0.02;
   private static final double DEFAULT_FORCE_VECTOR_SCALE = 1.0 / 50.0;

   private final SliderJoint xJoint, yJoint, zJoint;
   private final ExternalForcePoint externalForcePoint;

   private final Link zLink;

   public PointMassRobot()
   {
      this("PointMassRobot");
   }

   public PointMassRobot(String name)
   {
      this(name, DEFAULT_MASS, DEFAULT_RADIUS, DEFAULT_FORCE_VECTOR_SCALE);
   }

   public PointMassRobot(String name, double mass, double radius, double forceVectorScale)
   {
      super(name);

      this.setGravity(0.0);

      xJoint = new SliderJoint("pointMassX", new Vector3d(), this, Axis.X);
      Link xLink = new Link("xLink");
      xJoint.setLink(xLink);

      yJoint = new SliderJoint("pointMassY", new Vector3d(), this, Axis.Y);
      Link yLink = new Link("yLink");
      yJoint.setLink(yLink);

      zJoint = new SliderJoint("pointMassZ", new Vector3d(), this, Axis.Z);
      zLink = new Link("zLink");
      zLink.setMass(mass);
      zLink.setMomentOfInertia(0.0, 0.0, 0.0);
      Graphics3DObject zLinkLinkGraphics = new Graphics3DObject();
      zLinkLinkGraphics.addSphere(radius, YoAppearance.Gray());
      zLink.setLinkGraphics(zLinkLinkGraphics);
      zJoint.setLink(zLink);

      externalForcePoint = new ExternalForcePoint("ef_" + name, new Vector3d(), this.getRobotsYoVariableRegistry());
      zJoint.addExternalForcePoint(externalForcePoint);

      YoGraphicsListRegistry yoGraphicsListRegistry = new YoGraphicsListRegistry();
      YoGraphicPosition contactPoint = new YoGraphicPosition(name + "Point", externalForcePoint.getYoPosition(), radius, YoAppearance.Red());
      YoGraphicVector pointMassForce = new YoGraphicVector(name + "Force", externalForcePoint.getYoPosition(), externalForcePoint.getYoForce(), forceVectorScale, YoAppearance.Red());

      yoGraphicsListRegistry.registerYoGraphic(name, contactPoint);
      yoGraphicsListRegistry.registerYoGraphic(name, pointMassForce);

      this.addDynamicGraphicObjectsListRegistry(yoGraphicsListRegistry);

      addRootJoint(xJoint);
      xJoint.addJoint(yJoint);
      yJoint.addJoint(zJoint);
   }
   
   public PointMassRobot(String name, double mass)
   {
	   this(name, mass, DEFAULT_RADIUS,DEFAULT_FORCE_VECTOR_SCALE);
   }

   public void setPosition(double x, double y, double z)
   {
      xJoint.setQ(x);
      yJoint.setQ(y);
      zJoint.setQ(z);
   }

   public void setPosition(Point3d point)
   {
      xJoint.setQ(point.x);
      yJoint.setQ(point.y);
      zJoint.setQ(point.z);

   }

   public void setVelocity(double xd, double yd, double zd)
   {
      xJoint.setQd(xd);
      yJoint.setQd(yd);
      zJoint.setQd(zd);
   }

   public void setVelocity(Vector3d velocity)
   {
      xJoint.setQd(velocity.x);
      yJoint.setQd(velocity.y);
      zJoint.setQd(velocity.z);

   }

   public ExternalForcePoint getExternalForcePoint()
   {
      return externalForcePoint;
   }

   public void getPosition(Tuple3d tuple3d)
   {
      double x = xJoint.getQ().getDoubleValue();
      double y = yJoint.getQ().getDoubleValue();
      double z = zJoint.getQ().getDoubleValue();

      tuple3d.set(x, y, z);
   }

   public void getVelocity(Tuple3d tuple3d)
   {
      double xd = xJoint.getQD().getDoubleValue();
      double yd = yJoint.getQD().getDoubleValue();
      double zd = zJoint.getQD().getDoubleValue();

      tuple3d.set(xd, yd, zd);
   }

   public void setMass(double mass)
   {
      zLink.setMass(mass);
   }


}
