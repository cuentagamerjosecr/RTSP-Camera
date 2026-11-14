# RTSP Camera

Aplicación Android de ejemplo que expone un servidor RTSP local usando `RTSP-Server` de PedroSG94 y RootEncoder.

## Requisitos
- Android 8.0 (API 26) o superior.
- Cámara con soporte Camera2.
- Red Wi-Fi local para que otros dispositivos accedan al stream.
- El dispositivo debe permitir el uso de cámara en segundo plano mediante un servicio en primer plano.

## Compilar sin Android Studio
1. Crea un repositorio nuevo en GitHub.
2. Sube el contenido completo de este proyecto conservando las carpetas.
3. Abre la pestaña **Actions** y habilita los workflows si GitHub lo solicita.
4. Ejecuta **Build Android APK** con **Run workflow**, o realiza un `push`.
5. Cuando termine correctamente, abre la ejecución y descarga el artefacto **RTSP-Camera-debug**.
6. Descomprime el ZIP y encontrarás `app-debug.apk`.

## Uso
1. Instala el APK y concede permiso de cámara.
2. Activa audio solo si lo necesitas y concede permiso de micrófono.
3. Elige resolución, FPS, bitrate y cámara.
4. Pulsa **Iniciar transmisión**.
5. En VLC, en otro equipo conectado a la misma Wi-Fi, abre:
   `rtsp://IP_DEL_TELEFONO:8554/live`

## Notas técnicas y limitaciones
- `minSdk 26` (Android 8.0), `targetSdk 35`.
- La biblioteca de servidor RTSP crea el servidor; RootEncoder prepara el codificador de vídeo H.264 por hardware cuando el dispositivo ofrece un codificador compatible.
- Los FPS, tamaños y bitrates no son universales: dependen del teléfono, del sensor, del codificador y de la red. Una combinación no soportada producirá un error al iniciar.
- Los cambios de resolución/FPS/bitrate/cámara se aplican al reiniciar la transmisión; no se cambian todos en caliente.
- Esta primera versión funciona en modo sin vista previa para reducir consumo y mantener la cámara en el servicio.
- En Android 14 o superior, el servicio de cámara debe iniciarse desde una actividad visible después de conceder el permiso de cámara. No fuerces el inicio desde segundo plano.
- El APK de depuración no está firmado para distribución en Play Store.
- El comportamiento de pantalla apagada depende también de las políticas del fabricante y de la optimización de batería. Puede ser necesario excluir la app de dicha optimización desde Ajustes del teléfono.

## Dependencias
- `com.github.pedroSG94:RTSP-Server:1.4.3`
- `com.github.pedroSG94.RootEncoder:library:2.8.1`

Licencias de las dependencias: consulta sus repositorios oficiales antes de redistribuir.
