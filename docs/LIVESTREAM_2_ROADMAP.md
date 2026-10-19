# LiveStream 2.0 — desenvolvimento controlado

A versão estável permanece em `main`. Este ramo serve para alterações estruturais, sem interromper os utilizadores da versão anterior.

## Melhorias já iniciadas
- Separação entre thread de captura/codificação e thread de envio HTTP, com controlo de congestionamento: apenas um envio pendente.
- Consulta de imagens mais responsiva no navegador e ajuste da margem de áudio.

## Problemas que continuam abertos
- O transporte principal continua a ser JPEG/HTTP, não WebRTC.
- O áudio PCM por HTTP não tem uma fila de pacotes nem relógio sincronizado com o vídeo; blocos podem perder-se. Não é áudio profissional.
- A gravação MP4 simultânea não inclui som.
- A codificação gráfica ainda usa Bitmap -> textura OpenGL; falta captura direta para MediaCodec.
- Não existe autenticação de espectadores nem expiração dos links.
- Não há testes instrumentados em telemóveis reais nem testes de carga.

## Critérios para substituir a versão estável
1. Build Android e deploy do servidor sem erros.
2. Testar 30 e 60 minutos em dois telemóveis Android e em redes Wi-Fi/4G.
3. Confirmar MP4 reproduzível, sem perda de recursos após parar/reiniciar.
4. Confirmar captura de aplicação individual e ecrã completo.
5. Medir latência P50/P95, FPS, CPU, memória e bateria; não anunciar ganhos sem medições.
6. Implementar WebRTC (MediaCodec H.264 + Opus) com sinalização bidirecional, STUN/TURN e reconexão antes de o ativar como padrão.
7. Implementar controlos de acesso e expiração dos links.

## Nota
Captura de conteúdos com DRM ou FLAG_SECURE continua limitada pelo Android.
