# LiveStream 1.8 — câmaras e localização

Na app Android: botão «Câmaras + localização em direto». O modo de partilha de ecrã anterior mantém-se.

1. Iniciar transmissão e autorizar a câmara.
2. Opcional: ligar microfone, partilhar localização e testar as duas câmaras.
3. Enviar por WhatsApp. O recetor abre o link no Chrome/Safari e toca em Assistir; não precisa de instalar nada.
4. Terminar transmissão revoga o link e desliga câmara, microfone e GPS.

Aplicação web: https://reis-live-duo.a-miguel-reis.chatgpt.site
APK: separador Releases do repositório, após concluir o workflow «LiveStream 1.8 - APK com cameras e GPS».

## Limites de teste
- As duas câmaras em simultâneo dependem do dispositivo e WebView. Existe alternância quando não suportadas. Não simula uma segunda imagem.
- Manter esta app visível. Não existe captura de câmaras/GPS garantida em segundo plano neste modo.
- PeerJS Cloud e TURN público dependem de serviços externos. Algumas redes podem impedir a ligação; ainda precisa de teste em dois equipamentos e duas redes.
- A posição segue por WebRTC. O mapa OSM só é carregado após partilhar GPS e implica enviar a área ao fornecedor do mapa.
- Link de capacidade aleatório no fragmento: quem o recebe pode assistir. Não há gravação nem arquivo de coordenadas nesta função.
- A APK usa assinatura debug de teste do GitHub runner; não garante atualização sobre versões assinadas com outra chave. Para distribuição estável, configurar assinatura release persistente.
- O modo de partilha de ecrã existente depende do seu Worker anterior; este workflow não altera nem volta a publicar esse servidor.

Código de ligação/captura inspirado no LIVEQ v1.4 do mesmo autor, adaptado para convite individual, dois streams e localização.
