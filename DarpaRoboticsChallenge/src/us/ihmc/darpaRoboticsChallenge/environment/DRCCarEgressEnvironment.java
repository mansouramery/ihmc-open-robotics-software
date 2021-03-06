package us.ihmc.darpaRoboticsChallenge.environment;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import us.ihmc.darpaRoboticsChallenge.PolarisRobot;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearanceTexture;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.robotics.geometry.shapes.Box3d;
import us.ihmc.simulationconstructionset.ExternalForcePoint;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.util.environments.SelectableObjectListener;
import us.ihmc.simulationconstructionset.util.ground.CombinedTerrainObject3D;
import us.ihmc.simulationconstructionset.util.ground.RotatableBoxTerrainObject;
import us.ihmc.simulationconstructionset.util.ground.RotatableCinderBlockTerrainObject;
import us.ihmc.simulationconstructionset.util.ground.TerrainObject3D;

public class DRCCarEgressEnvironment implements CommonAvatarEnvironmentInterface
{
   private final double edgeOfStepX = -0.8, edgeOfStepY = 0.5;
   private final static Point3d stepDimensions = new Point3d(1.0, 0.3, 0.2);
   private final static Point3d carDimensions = new Point3d(1.0, 0.6, 0.4);
   private final static Vector3d polarisPosition = new Vector3d(-0.4, 1.5, 0.0);
   private final CombinedTerrainObject3D terrain = new CombinedTerrainObject3D("drcCarEgressTerrain");
   private final List<Robot> robots = new ArrayList<Robot>();
   
   public DRCCarEgressEnvironment()
   {
      RigidBodyTransform locationStep = new RigidBodyTransform();
      locationStep.setTranslation(new Vector3d(edgeOfStepX + 0.5 * stepDimensions.x, edgeOfStepY + 0.5 * stepDimensions.y, stepDimensions.z / 2.0));
      Box3d stepBox = new Box3d(locationStep, stepDimensions.x, stepDimensions.y, stepDimensions.z);
      terrain.addTerrainObject(new RotatableCinderBlockTerrainObject(stepBox, YoAppearance.DarkGray()));   
      
      RigidBodyTransform locationCar = new RigidBodyTransform();
      locationCar.setTranslation(new Vector3d(edgeOfStepX + 0.5 * stepDimensions.x, edgeOfStepY + stepDimensions.y + 0.5 * carDimensions.y, carDimensions.z / 2.0));
      Box3d carBox = new Box3d(locationCar, carDimensions.x, carDimensions.y, carDimensions.z);      
      terrain.addTerrainObject(new RotatableCinderBlockTerrainObject(carBox, YoAppearance.DarkGray()));   
      
      terrain.addTerrainObject(setUpGround("ground"));
      
      RigidBodyTransform polarisTransform = new RigidBodyTransform();
      polarisTransform.setTranslation(polarisPosition);
      robots.add(new PolarisRobot("polaris", polarisTransform));
   }
   
   @Override
   public TerrainObject3D getTerrainObject3D()
   {
      return terrain;
   }

   @Override
   public List<? extends Robot> getEnvironmentRobots()
   {
      return robots;
   }

   @Override
   public void createAndSetContactControllerToARobot()
   {
   }

   @Override
   public void addContactPoints(List<? extends ExternalForcePoint> externalForcePoints)
   {      
   }

   @Override
   public void addSelectableListenerToSelectables(SelectableObjectListener selectedListener)
   {      
   }
   
   private static CombinedTerrainObject3D setUpGround(String name)
   {
      CombinedTerrainObject3D combinedTerrainObject = new CombinedTerrainObject3D(name);

      YoAppearanceTexture texture = new YoAppearanceTexture("Textures/brick.png");

      RigidBodyTransform location = new RigidBodyTransform();
      location.setTranslation(new Vector3d(0, 0, -0.5));

      RotatableBoxTerrainObject newBox = new RotatableBoxTerrainObject(new Box3d(location, 10, 10, 1), texture);
      combinedTerrainObject.addTerrainObject(newBox);

      return combinedTerrainObject;
   }
}
