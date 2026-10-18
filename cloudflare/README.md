# Relay gratuito Cloudflare Workers (protótipo)
Este relay permite ver imagens sucessivas do ecrã num navegador. **Não é ainda vídeo WebRTC nem transmite áudio.**
O uso do plano gratuito está sujeito aos limites atuais da Cloudflare. Não garante muitos espectadores nem tráfego ilimitado.

## Publicar uma vez
1. Criar uma conta gratuita em https://dash.cloudflare.com/ (não adicionar cartão nem ativar plano pago).
2. Instalar Node.js no computador e executar, na pasta cloudflare: `npm install` e `npx wrangler login`.
3. Executar `npm run deploy`. Guardar o endereço HTTPS `https://livestream-free.<subdominio>.workers.dev`.
4. No repositório GitHub, abrir **Settings → Secrets and variables → Actions → Variables → New repository variable**.
5. Criar a variável `LIVESTREAM_SERVER_URL` com o endereço do passo 3 (sem barra final).
6. Abrir **Actions → Build Android APK → Run workflow**. Instalar a APK criada em **Artifacts**.

Depois disso, a app já não pede o endereço ao utilizador.

## Privacidade e limites
O link dá acesso a quem o receber. Não partilhar ecrãs com dados privados.
Este protótipo usa uma chave de publicação separada do link público, mas não tem contas, controlo de acesso, nem encerramento remoto imediato da última imagem; a imagem expira após 12 segundos.
O relay mantém as imagens temporariamente na memória da instância; reinícios ou limites da plataforma interrompem a transmissão.
Áudio de aplicações protegidas por DRM e imagem protegida não podem ser contornados.
