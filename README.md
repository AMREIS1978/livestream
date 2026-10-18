# LiveStream

Aplicação Android para partilhar o ecrã e áudio permitido do telemóvel através de um link que pode ser enviado por WhatsApp.

## Experiência pretendida

1. O utilizador abre a app e inicia a partilha com consentimento explícito do Android.
2. A app executa um serviço em primeiro plano com notificação visível enquanto transmite, mesmo que a interface da app esteja em segundo plano.
3. É criado um link de sessão para abrir no navegador, sem conta ou palavra-passe para espectadores.
4. A app permite copiar o link, partilhar por WhatsApp, ver o número de espectadores e terminar a transmissão.

## Arquitetura proposta

- Android nativo Kotlin, MediaProjection para imagem e AudioPlaybackCapture para áudio permitido.
- WebRTC com sinalização e infraestrutura de retransmissão para vários espectadores.
- Página web para assistir em telemóvel ou computador.
- GitHub Actions para compilar APK de teste e atualizar a mesma aplicação com assinatura estável.

## Limitações e segurança

- Cada sessão de captura requer consentimento do utilizador conforme as regras do Android.
- Áudio e vídeo protegidos por DRM, incluindo certos serviços de televisão, podem ser bloqueados e não serão contornados.
- Um link sem autenticação pode ser reencaminhado; cada sessão deverá usar identificador imprevisível e expirar quando terminar.
- Não publicar chaves privadas, tokens ou credenciais no repositório.

## Estado

Repositório inicializado. Código Android, backend e APK ainda por implementar.
