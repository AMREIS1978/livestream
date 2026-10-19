# LiveStream Windows

A versão Windows utiliza Python, Tkinter, MSS e Pillow (gratuitos). A transmissão JPEG utiliza o mesmo servidor e os mesmos links da versão Android.

## EXE
GitHub Actions > Build Windows EXE > execução concluída > Artifacts > livestream-windows-exe.

## Gravação MP4
Instale FFmpeg gratuitamente e coloque ffmpeg.exe no PATH. A opção de gravação usa libx264 e 5 FPS nesta versão inicial. O EXE inclui a aplicação Python, mas **não inclui FFmpeg**. O envio de imagens continua a usar JPEG e CPU; H.264 por hardware e WebRTC são melhorias futuras.

A aplicação Windows é experimental e deve ser testada antes de uso prolongado.
