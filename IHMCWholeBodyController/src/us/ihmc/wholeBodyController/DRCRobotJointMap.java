package us.ihmc.wholeBodyController;

import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;

import us.ihmc.SdfLoader.SDFHumanoidJointNameMap;
import us.ihmc.SdfLoader.partNames.ArmJointName;
import us.ihmc.SdfLoader.partNames.LegJointName;
import us.ihmc.SdfLoader.partNames.NeckJointName;
import us.ihmc.SdfLoader.partNames.SpineJointName;
import us.ihmc.robotics.controllers.YoPDGains;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;

public interface DRCRobotJointMap extends SDFHumanoidJointNameMap
{
   public abstract String getNameOfJointBeforeChest();

   public abstract SideDependentList<String> getNameOfJointBeforeThighs();

   public abstract SideDependentList<String> getNameOfJointBeforeHands();

   public abstract String[] getOrderedJointNames();

   public abstract String getLegJointName(RobotSide robotSide, LegJointName legJointName);

   public abstract String getArmJointName(RobotSide robotSide, ArmJointName armJointName);

   public abstract String getNeckJointName(NeckJointName neckJointName);

   public abstract String getSpineJointName(SpineJointName spineJointName);

   public abstract String[] getPositionControlledJointsForSimulation();

   public abstract RobotContactPointParameters getContactPointParameters();

   public List<ImmutablePair<String, YoPDGains>> getPassiveJointNameWithGains(YoVariableRegistry registry);
}