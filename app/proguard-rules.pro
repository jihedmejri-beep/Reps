
# --- Gson -------------------------------------------------------------------
# Gson reads and writes fields by name through reflection. R8 would rename or
# strip them, so a release build could neither parse the RepsAI response nor
# read back a session draft written by an earlier version of the app.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# RepsAI request/response bodies (snake_case names come from @SerializedName).
-keep class com.reps.app.ai.RepsAiApiService$* { <fields>; <init>(...); }

# The in-progress workout is persisted as JSON in active_session_drafts.
-keep class com.reps.app.domain.model.SessionDraft { <fields>; <init>(...); }
-keep class com.reps.app.domain.model.RestState { <fields>; <init>(...); }
-keep class com.reps.app.domain.model.WorkoutExercise { <fields>; <init>(...); }
-keep class com.reps.app.domain.model.ExerciseSet { <fields>; <init>(...); }

-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
