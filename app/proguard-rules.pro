# kotlinx.serialization, OkHttp, Room и Coil приносят свои правила.
# Модели постов хранятся в базе как JSON — имена полей должны пережить сжатие.
-keepclassmembers @kotlinx.serialization.Serializable class app.dudebooru.** {
    *** Companion;
    *** serializer(...);
}
