package io.github.sndnv.layers.service.actions

/**
  * An action definition is the combination of an [[Action]] and the [[ActionTrigger]]
  * that is expected to trigger that action.
  * <br/><br/>
  * The default usage is to have actions defined as code, triggers defined as configuration
  * and both are combined at startup in `ActionDefinition`s, which are then used by an executor.
  *
  * @see [[ActionDefinition.WithSchedule]] for schedule-based action definitions
  * @see [[ActionDefinition.WithEvent]] for event-based action definitions
  * @see [[Action]] for general action information and examples on creating actions
  * @see [[ActionTrigger]] for trigger information and configuration
  * @see [[ActionExecutor]] for information about the component that manages the actions
  */
sealed trait ActionDefinition {

  /**
    * Action to be executed based on the specified trigger.
    */
  def action: Action

  /**
    * Trigger that will cause the action to be executed.
    */
  def trigger: ActionTrigger
}

/**
  * @see [[ActionDefinition]]
  */
object ActionDefinition {

  /**
    * Schedule-based action definition.
    *
    * @param action target action
    * @param trigger schedule-based trigger
    */
  final case class WithSchedule(
    override val action: Action.WithSchedule,
    override val trigger: ActionTrigger.Schedule
  ) extends ActionDefinition

  /**
    * Event-based action definition.
    *
    * @param action target action
    * @param trigger event-based trigger
    */
  final case class WithEvent(
    override val action: Action.WithEvent,
    override val trigger: ActionTrigger.Event
  ) extends ActionDefinition
}
