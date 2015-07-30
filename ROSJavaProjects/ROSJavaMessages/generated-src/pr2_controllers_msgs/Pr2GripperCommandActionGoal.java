package pr2_controllers_msgs;

public interface Pr2GripperCommandActionGoal extends org.ros.internal.message.Message {
  static final java.lang.String _TYPE = "pr2_controllers_msgs/Pr2GripperCommandActionGoal";
  static final java.lang.String _DEFINITION = "# ====== DO NOT MODIFY! AUTOGENERATED FROM AN ACTION DEFINITION ======\n\nHeader header\nactionlib_msgs/GoalID goal_id\nPr2GripperCommandGoal goal\n";
  std_msgs.Header getHeader();
  void setHeader(std_msgs.Header value);
  actionlib_msgs.GoalID getGoalId();
  void setGoalId(actionlib_msgs.GoalID value);
  pr2_controllers_msgs.Pr2GripperCommandGoal getGoal();
  void setGoal(pr2_controllers_msgs.Pr2GripperCommandGoal value);
}