# Random Reminders

Aplicativo Android para criar lembretes que tocam em horários aleatórios dentro de uma janela diária definida pelo usuário. Cada lembrete tem seu próprio ciclo, intervalo mínimo entre disparos, recorrência, texto e toque. As regras e o histórico são armazenados localmente.

## Funcionalidades

- Criar, editar, ativar, pausar e excluir vários lembretes independentes.
- Definir o nome e um texto opcional para a notificação.
- Escolher o início e o fim da janela diária, o número de disparos por ciclo e o espaçamento mínimo entre eles.
- Repetir a cada N dias (1 significa todos os dias) ou executar uma única vez.
- Escolher o toque do alarme, usar o padrão do Android ou deixar o lembrete silencioso.
- O alarme é registrado no Android como um alarme de verdade (`setAlarmClock`), com ícone de alarme na barra de status e tratamento de alarme pelo sistema. Ao tocar, abre uma tela cheia com a opção de pará-lo; o som toca por até 15 segundos, enquanto a tela permanece aberta até o usuário fechá-la. Quando permitido pelo Android, a tela aparece mesmo com o aparelho bloqueado ou com a tela desligada.
- Pesquisar lembretes por nome ou descrição e, opcionalmente, exibir o próximo horário na lista.
- Consultar o histórico dos disparos.
- Persistir regras e disparos pendentes localmente e reagendar após reinicializações e mudanças de hora/fuso.

O aplicativo não declara permissão de Internet. Regras, horários pendentes e histórico ficam no banco Room do dispositivo; não há sincronização com servidor.

## Como funciona

1. Ao ativar uma regra, o app valida seus parâmetros, fixa a data-base da recorrência, sorteia os horários do ciclo e persiste os horários no Room.
2. O `AlarmManager` agenda somente o próximo horário daquela regra usando `setAlarmClock`, para que o Android o reconheça como alarme. Os demais horários do ciclo permanecem persistidos localmente.
3. Quando o alarme dispara, o app registra o horário no histórico, mostra uma notificação de alarme, toca o som escolhido no stream de alarme do Android por até 15 segundos, abre a tela do alarme para o usuário fechá-la e agenda o próximo disparo.
4. Ao terminar o ciclo, uma regra recorrente gera o próximo ciclo no dia permitido. Uma regra configurada para executar uma vez é desativada ao terminar.
5. Após reiniciar o aparelho ou alterar o relógio/fuso, os receivers do app tentam reagendar as regras ativas. Disparos que ficaram no passado são descartados, não executados em atraso.

“Parar alarme” encerra o aviso atual.

### Regras de horário

- A janela deve começar antes de terminar e ocorre no mesmo dia; janelas que atravessam a meia-noite não são aceitas.
- A quantidade de disparos deve ser positiva e o espaçamento mínimo deve ser de pelo menos um minuto.
- A validação verifica se o intervalo mínimo cabe na janela antes de permitir salvar.
- O sorteio distribui os disparos na janela e garante que horários vizinhos respeitem o espaçamento mínimo configurado.
- Ao reativar uma regra, uma nova data-base e um novo ciclo de horários são gerados.
- Os horários são locais ao dispositivo. O comportamento final de alarmes e notificações também depende das configurações e restrições da versão do Android e do fabricante.

## Permissões e comportamento do Android

| Permissão | Para que serve |
|---|---|
| `POST_NOTIFICATIONS` | Exibir notificações no Android 13 ou superior. Se negada ou se as notificações estiverem desativadas, o app não consegue mostrar o lembrete. |
| `SCHEDULE_EXACT_ALARM` | Permitir alarmes pontuais no Android 12. Se não estiver disponível, o app usa um alarme inexato, que pode atrasar. |
| `USE_EXACT_ALARM` | Conceder alarmes exatos automaticamente no Android 13+, por se tratar de um app de alarmes. |
| `RECEIVE_BOOT_COMPLETED` | Reagendar regras ativas após a inicialização do aparelho. |
| `USE_FULL_SCREEN_INTENT` | Solicitar ao Android a apresentação da tela do alarme com o aparelho bloqueado. No Android 14+, o usuário pode conceder ou revogar essa permissão nas configurações do sistema; o app mostra um atalho para essa tela quando necessário. |
| `VIBRATE` | Permitir vibração da notificação. |

O sistema pode limitar alarmes em segundo plano, notificações em tela cheia, sons e vibrações. Alarmes inexatos não garantem o minuto configurado. A disponibilidade e o fluxo de concessão das permissões podem variar entre versões do Android e fabricantes.

## Tecnologias

| Área | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.0.21 |
| Interface | Jetpack Compose e Material 3 |
| Navegação | Navigation Compose |
| Arquitetura | ViewModels, estado reativo com `Flow` e separação entre UI, domínio, dados e alarmes |
| Injeção de dependências | Hilt com KSP |
| Persistência | Room |
| Agendamento | `AlarmManager` e `BroadcastReceiver` |
| Concorrência | Kotlin Coroutines |
| Build | Gradle 8.10.2, Android Gradle Plugin 8.7.3 e Kotlin DSL |

## Requisitos

- JDK 17 ou superior.
- Android SDK com a plataforma Android 35 instalada.
- Gradle Wrapper incluído no repositório; não é necessário instalar Gradle separadamente.
- Para executar no dispositivo: Android 8.0 (API 26) ou superior. Para instalar por linha de comando, `adb` também deve estar disponível.

O projeto define `minSdk 26`, `compileSdk 35`, `targetSdk 35` e bytecode JVM 17.

## Build, testes e instalação

Execute os comandos na raiz do repositório. No Windows, use `gradlew.bat`; no macOS/Linux, use `./gradlew`.

```shell
# Compilar a variante debug
./gradlew :app:assembleDebug

# Executar os testes unitários
./gradlew :app:testDebugUnitTest
```

No Windows, os comandos equivalentes são:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

O APK de depuração é gerado em `app/build/outputs/apk/debug/app-debug.apk`. Com um dispositivo ou emulador conectado e `adb` configurado, instale-o com:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### APK de release assinado

A configuração de release exige `signing.properties` na raiz do projeto e o keystore indicado em `storeFile`. O arquivo deve conter estas propriedades:

```properties
storeFile=app/random-reminders-release.p12
storePassword=<senha-do-keystore>
keyAlias=<alias-da-chave>
keyPassword=<senha-da-chave>
```

`storeFile` pode ser um caminho relativo à raiz do projeto ou absoluto. O arquivo de configuração e o keystore são locais e ignorados pelo Git; não use credenciais reais em arquivos versionados. O build falha explicitamente se o arquivo, alguma propriedade ou o keystore estiver ausente.

Para gerar o APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

O arquivo será gerado em `app/build/outputs/apk/release/app-release.apk`. Mantenha uma cópia segura do keystore e das credenciais: a mesma chave será necessária para assinar futuras atualizações do aplicativo.

Os diretórios `build/` são artefatos locais de compilação e estão excluídos do Git pelo `.gitignore`.

Os testes unitários cobrem a geração de horários e seus espaçamentos, a recorrência, a validação do formulário e a resolução do texto da notificação.

## Estrutura do projeto

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/raulh/randomreminders/
│       │   │   ├── alarms/       # AlarmManager, receivers e notificações
│       │   │   ├── data/         # Repositório, Room, entidades e DAOs
│       │   │   ├── di/           # Configuração de injeção de dependências
│       │   │   ├── domain/       # Modelo, validação e geração de horários
│       │   │   └── ui/           # Lista, editor, histórico e tema
│       │   └── res/              # Textos, ícones e temas
│       └── test/                 # Testes unitários
├── gradle/
│   ├── libs.versions.toml        # Versões e catálogo de dependências
│   └── wrapper/                 # Configuração do Gradle Wrapper
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

### Componentes principais

- `domain/model/ReminderRule`: modelo de regra e validação centralizada de parâmetros e viabilidade da janela.
- `domain/RandomTimeGenerator`: geração pura e testável dos horários e cálculo dos dias permitidos.
- `domain/TimeText` e `domain/NotificationMessage`: conversão de horários e resolução do texto da notificação.
- `data/ReminderRepository` e `data/db`: persistência de regras, disparos pendentes e histórico.
- `alarms/AlarmScheduler`: ativação, cancelamento e reagendamento de ciclos.
- `alarms/AlarmReceiver` e `alarms/BootReceiver`: processamento de disparos e recuperação após eventos do sistema.
- `alarms/AlarmActivity`: tela cheia do alarme, aberta no disparo para permitir parar o som.
- `alarms/NotificationHelper`: canais, notificação de alarme e som no stream de alarme.
- `ui/rulelist`, `ui/ruledit` e `ui/history`: telas e seus ViewModels.

## Limitações conhecidas

- O app não sincroniza dados entre dispositivos nem oferece cópia de segurança/exportação.
- A precisão depende da permissão de alarmes exatos e das políticas de economia de bateria do dispositivo.
- Se a permissão de notificação estiver ausente, o disparo ainda toca o som e abre a tela do alarme, mas não mostra a notificação.
- Com o aparelho desbloqueado, o Android costuma apresentar o full-screen intent como um heads-up; o app também tenta abrir a tela do alarme diretamente. Se o fabricante bloquear esse launch em segundo plano, a notificação continua disponível para abrir a tela.
- Excluir um lembrete também exclui seus disparos pendentes e o histórico associado.
- O app não oferece suporte a janelas que cruzam a meia-noite.

## Licença

Este projeto é dedicado ao domínio público sob os termos da [Unlicense](./LICENSE). Você pode usar, copiar, modificar, publicar, distribuir, sublicenciar e/ou vender cópias do projeto sem restrições de copyright, conforme permitido por lei.
