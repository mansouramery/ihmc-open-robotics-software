package object_manipulation_msgs;

public interface GraspPlanningAction extends org.ros.internal.message.Message {
  static final java.lang.String _TYPE = "object_manipulation_msgs/GraspPlanningAction";
  static final java.lang.String _DEFINITION = "# ====== DO NOT MODIFY! AUTOGENERATED FROM AN ACTION DEFINITION ======\n\nGraspPlanningActionGoal action_goal\nGraspPlanningActionResult action_result\nGraspPlanningActionFeedback action_feedback\n";
  object_manipulation_msgs.GraspPlanningActionGoal getActionGoal();
  void setActionGoal(object_manipulation_msgs.GraspPlanningActionGoal value);
  object_manipulation_msgs.GraspPlanningActionResult getActionResult();
  void setActionResult(object_manipulation_msgs.GraspPlanningActionResult value);
  object_manipulation_msgs.GraspPlanningActionFeedback getActionFeedback();
  void setActionFeedback(object_manipulation_msgs.GraspPlanningActionFeedback value);
}