package actionlib;

public interface TestRequestAction extends org.ros.internal.message.Message {
  static final java.lang.String _TYPE = "actionlib/TestRequestAction";
  static final java.lang.String _DEFINITION = "# ====== DO NOT MODIFY! AUTOGENERATED FROM AN ACTION DEFINITION ======\n\nTestRequestActionGoal action_goal\nTestRequestActionResult action_result\nTestRequestActionFeedback action_feedback\n";
  actionlib.TestRequestActionGoal getActionGoal();
  void setActionGoal(actionlib.TestRequestActionGoal value);
  actionlib.TestRequestActionResult getActionResult();
  void setActionResult(actionlib.TestRequestActionResult value);
  actionlib.TestRequestActionFeedback getActionFeedback();
  void setActionFeedback(actionlib.TestRequestActionFeedback value);
}