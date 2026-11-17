# FotoTimer

App Android simples para **tirar fotos automaticamente em intervalos**, mesmo com a tela apagada.
Ideal para timelapse, acompanhar uma obra, uma planta crescendo ou qualquer coisa que muda com o tempo.

## Funcionalidades

- **Quantidade fixa ou sem limite**: tira, por exemplo, 100 fotos a cada 30 s, ou continua até você parar.
- **Intervalo configurável**: de 3 segundos a 24 horas, com atalhos rápidos (5 s, 30 s, 1 min, 5 min…).
- **Agendamento**: escolha data e hora para a sessão começar sozinha.
- **Tela apagada / segundo plano**: a captura continua com o celular bloqueado.
- **Câmera traseira ou frontal**.
- **Data e hora na foto** (opcional), gravadas no canto da imagem.
- **Pastas**: crie, renomeie e apague pastas dentro de `Pictures/FotoTimer` e escolha onde as fotos vão.
- **Galeria interna** com data e hora de cada foto, filtro por pasta, mover, compartilhar e apagar.
- **Notificação** com progresso, contagem regressiva e botão Parar.

## Instalar

1. Baixe o `FotoTimer.apk` na página de [Releases](../../releases).
2. Abra o arquivo no celular e permita "instalar apps desta fonte".
3. Na primeira vez, permita **Câmera** e **Notificações**.
4. Recomendado: em Configurações › Apps › FotoTimer › Bateria, escolha **Sem restrições**, para o Android não pausar as fotos com a tela apagada.

Requer **Android 10** ou mais novo.

## Compilar

Precisa do JDK 17+ e do Android SDK (API 36).

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

O projeto não usa bibliotecas externas: só a API do Android (Camera2, MediaStore, serviço em primeiro plano).

## Limitações conhecidas

- As fotos saem na orientação retrato.
- Um agendamento se perde se o celular for reiniciado ou o app for fechado à força.
- Algumas marcas (Xiaomi, Samsung, Motorola) têm economia de bateria extra que pode pausar o app; veja a tela de Dicas no app.
