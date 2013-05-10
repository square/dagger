Example: Android Activity Graphs
================================

Building on top of the simple Android example, this example demonstrates how it is possible to
create child graphs for each activity which extend from the global graph.

Some of the advantages of the activity scope:

 * Provides the ability to inject objects which require the activity to be constructed.
 * Allows for the use of singletons on a per-activity basis. This is a great way to manage a
   resource that is shared by a bunch of fragments in an activity.
 * Keeps the global object graph clear of things that can be used only by activities.

While this example only shows the presence of an activity scope, you should be able to see the
potential for other useful scopes that can be used. For example, having a dedicated object graph
for the current user session is a great way to manage data that is tied to the currently logged-in
user.

_Note: The app does not actually do anything when it is run. It is only to show how you can
 structure Dagger within an Android app_
