# LiveStream — Android

Aplicação experimental de partilha do ecrã do Android por link.

## Interface
- **Iniciar partilha do ecrã**: solicita a autorização oficial do Android.
- **Enviar link pelo WhatsApp**: abre a partilha de um endereço público para os espectadores.
- **Parar transmissão**: termina o serviço de captura.

## Estado real
- A captura de imagem do Android e o envio periódico de imagens JPEG estão implementados.
- **Não existe ainda áudio**, vídeo fluido/WebRTC, contador de espectadores ou servidor público publicado.
- O link só funciona depois de publicar o relay gratuito e configurar o URL na compilação.
- Não contorna proteções DRM de aplicações de televisão ou de outros serviços.
- O protótipo deve ser utilizado apenas para testes com conteúdos que tenha autorização para transmitir.

## Publicação gratuita
Consulte [cloudflare/README.md](cloudflare/README.md) para publicar o relay na Cloudflare Workers e configurar a variável GitHub Actions `LIVESTREAM_SERVER_URL`.
Depois, a compilação da APK é automática em [GitHub Actions](../../actions). Sem a variável, a APK informa que falta publicar o servidor em vez de criar um link inutilizável.

## Desenvolvimento
- Android Kotlin / MediaProjection / serviço foreground
- Cloudflare Workers + Durable Objects como relay experimental
- GitHub Actions para gerar APK de teste
- Plano gratuito sujeito aos limites dos fornecedores. Sem garantia de espectadores ilimitados.

## Próximas etapas
1. Validar compilação Android e teste real entre dois telemóveis.
2. Substituir JPEG por WebRTC ou outro transporte de vídeo eficiente.
3. Capturar e transmitir apenas o áudio permitido pelo Android.
4. Implementar autenticação robusta do publicador, controlo de sessões, expiração e testes de carga.
