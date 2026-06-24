# Changelog
All notable changes to this application will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this application adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Supported section titles:
- Adicionado, Alterado, Corrigido, Removido

**Tem em atenção que este changelog foca-se principalmente em modificações relacionadas com o utilizador, dando ênfase
a alterações que podem ter impacto direto nos utilizadores, em vez de destacar outras atualizações arquiteturais importantes.**

## [Unreleased]

## [3.5.3 (1745)] - 2026-06-05

### Alterado:
- Removemos da lista de servidores os servidores agendados para desativação.

## [3.5.2 (1742)] - 2026-06-04

### Alterado:
- Atualizámos a compatibilidade com as mais recentes alterações da rede Zcash. Esta atualização é necessária para garantir o funcionamento contínuo da carteira.
- Atualizámos o servidor predefinido.

## [3.5.1 (1741)] - 2026-06-02

### Alterado:
- Atualizámos a compatibilidade com as mais recentes alterações da rede Zcash. Esta atualização é necessária para garantir o funcionamento contínuo da carteira.
- Atualizámos o servidor predefinido.

## [3.5.0 (1736)] - 2026-05-28

### Adicionado:
- A Votação de Detentores de Moeda permite-te votar na governação da Zcash de forma privada, diretamente a partir das tuas carteiras Zodl e Keystone.

## [3.4.1 (1698)] - 2026-05-19

### Alterado:
- Atualizámos o texto no widget de estado da proteção.

### Corrigido:
- Corrigimos um erro que impedia a proteção quando estavam envolvidas muitas entradas transparentes pequenas.
- Corrigimos o "Enviar novamente" que preenchia incorretamente alguns campos.
- Corrigimos o aviso de Conversão de Moeda que não aparecia nos fluxos de nova carteira e de restauração.

## [3.4.0 (1691)] - 2026-05-12

### Adicionado:
- Adicionámos a Altura de Nascimento da Carteira ao conectar uma Keystone.

### Alterado:
- Renovámos o texto do fluxo de Restauração.

### Corrigido:
- Erros de localização que podiam truncar ZEC, bloquear o Envio ou causar um envio excessivo com um separador decimal de vírgula.
- Um erro em que um endereço protegido podia ser reutilizado como endereço de reembolso de swap.
- Um aviso de confirmação de cópia em falta no ecrã Receber.
- A sincronização por Tor que se desligava após uma interrupção por falta de espaço em disco, além de outras correções de UX/UI.

## [3.3.1 (1643)] - 2026-04-10

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.1 (1641)] - 2026-04-10

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.1 (1639)] - 2026-04-09

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.0 (1637)] - 2026-04-08

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.0 (1635)] - 2026-04-08

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.0 (1631)] - 2026-04-07

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.0 (1629)] - 2025-04-07

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.3.0 (1627)] - 2025-04-03

### Adicionado:
- Adicionámos uma funcionalidade para desconectar uma carteira de hardware Keystone.

### Alterado:
- Atualizámos todas as dependências.

### Corrigido:
- Corrigimos alguns problemas de UX/UI.

## [3.2.1 (1605)] - 2025-03-28

### Adicionado:
- Corrigimos a funcionalidade Mostrar/Ocultar e adicionámo-la ao Swap a partir de ZEC.
- Adicionámos um aviso de proteção Tor no Widget de Estado da Carteira.

### Alterado:
- Mudámos o Swap a partir de ZEC para usar FLEX_INPUT, permitindo que swaps com envio insuficiente continuem a ser executados.
- Atualizámos e unificámos os elementos e comportamentos de UX/UI em ambas as plataformas.

### Corrigido:
- Corrigimos vários problemas no processamento de pedidos de pagamento ZIP321.

## [3.2.0 (1600)] - 2025-03-24

### Adicionado:
- Corrigimos a funcionalidade Mostrar/Ocultar e adicionámo-la ao Swap a partir de ZEC.
- Adicionámos um aviso de proteção Tor no Widget de Estado da Carteira.

### Alterado:
- Mudámos o Swap a partir de ZEC para usar FLEX_INPUT, permitindo que swaps com envio insuficiente continuem a ser executados.
- Atualizámos e unificámos os elementos e comportamentos de UX/UI em ambas as plataformas.

### Corrigido:
- Corrigimos vários problemas no processamento de pedidos de pagamento ZIP321.

## [3.2.0 (1594)] - 2026-03-20

### Adicionado:
- Corrigimos a funcionalidade Mostrar/Ocultar e adicionámo-la ao Swap a partir de ZEC.
- Adicionámos um aviso de proteção Tor no Widget de Estado da Carteira.

### Alterado:
- Mudámos o Swap a partir de ZEC para usar FLEX_INPUT, permitindo que swaps com envio insuficiente continuem a ser executados.
- Atualizámos e unificámos os elementos e comportamentos de UX/UI em ambas as plataformas.

### Corrigido:
- Corrigimos vários problemas no processamento de pedidos de pagamento ZIP321.

## [3.1.0 (1516)] - 2026-03-06

### Adicionado:
- Criámos um mecanismo na app para contactar o suporte em caso de problemas com Swap/Pay.
- Adicionámos um aviso de slippage baixo.
- Implementámos o tratamento de depósitos incompletos.
- Adicionámos também mais informação aos fluxos de estado de Swap/Pay.

### Alterado:
- Atualizámos o slippage predefinido para 2%, para uma execução mais rápida dos swaps.
- Alterámos o servidor predefinido.
- Melhorámos também a UX do ecrã de depósito do Swap para ZEC.
- Fizemos outras melhorias de UX/UI.

### Corrigido:
- Corrigimos um erro na árvore de compromissos de notas (note commitment tree).

## [3.1.0 (1514)] - 2026-03-06

### Adicionado:
- Criámos um mecanismo na app para contactar o suporte em caso de problemas com Swap/Pay.
- Adicionámos um aviso de slippage baixo.
- Implementámos o tratamento de depósitos incompletos.
- Adicionámos também mais informação aos fluxos de estado de Swap/Pay.

### Alterado:
- Atualizámos o slippage predefinido para 2%, para uma execução mais rápida dos swaps.
- Alterámos o servidor predefinido.
- Melhorámos também a UX do ecrã de depósito do Swap para ZEC.
- Fizemos outras melhorias de UX/UI.

### Corrigido:
- Corrigimos um erro na árvore de compromissos de notas (note commitment tree).

## [3.0.1 (1470)] - 2026-03-02

### Alterado:
- Atualização de marca Zashi -> Zodl - mudámos a marca de Zashi para Zodl, sem afetar a experiência do utilizador.
- Implementámos melhorias de UX/UI no Swap/Pay.

### Corrigido:
- Corrigimos uma série de erros e problemas reportados pelos utilizadores.

## [3.0.1 (1469)] - 2026-02-27

### Alterado:
- Atualização de marca Zashi -> Zodl - mudámos a marca de Zashi para Zodl, sem afetar a experiência do utilizador.
- Implementámos melhorias de UX/UI no Swap/Pay.

### Corrigido:
- Corrigimos uma série de erros e problemas reportados pelos utilizadores.

## [3.0.0 (1468)] - 2026-02-26

### Alterado:
- Atualização de marca Zashi -> Zodl - mudámos a marca de Zashi para Zodl, sem afetar a experiência do utilizador.
- Implementámos melhorias de UX/UI no Swap/Pay.

### Corrigido:
- Corrigimos uma série de erros e problemas reportados pelos utilizadores.

## [3.0.0 (1467)] - 2026-02-25

### Alterado:
- Atualização de marca Zashi -> Zodl - mudámos a marca de Zashi para Zodl, sem afetar a experiência do utilizador.
- Implementámos melhorias de UX/UI no Swap/Pay.

### Corrigido:
- Corrigimos uma série de erros e problemas reportados pelos utilizadores.

## [3.0.0 (1466)] - 2026-02-25

### Alterado:
- Atualização de marca Zashi -> Zodl - mudámos a marca de Zashi para Zodl, sem afetar a experiência do utilizador.
- Implementámos melhorias de UX/UI no Swap/Pay.

### Corrigido:
- Corrigimos uma série de erros e problemas reportados pelos utilizadores.

## [2.4.11 (1424)] - 2026-01-21

### Adicionado:
- Atualizámos os ícones dos ativos e redes do Swap/Pay.

### Alterado:
- Introduzimos um novo estado Pendente para os fluxos de transação, que substituiu os nossos estados de Erro.
- Fizemos atualizações de ícones e texto.
- Melhorámos o nosso tratamento de erros.
- Movemos a Conversão de Moeda das Definições Avançadas para o menu Mais opções.

### Corrigido:
- Melhorámos a UI/UX do nosso tratamento de erros.
- Melhorámos a experiência de transferência dos parâmetros Sapling.
- Corrigimos alguns problemas e falhas reportados pelos utilizadores.

## [2.4.11 (1419)] - 2025-12-18

### Adicionado:
- Atualizámos os ícones dos ativos e redes do Swap/Pay.

### Alterado:
- Introduzimos um novo estado Pendente para os fluxos de transação, que substituiu os nossos estados de Erro.
- Fizemos atualizações de ícones e texto.
- Melhorámos o nosso tratamento de erros.
- Movemos a Conversão de Moeda das Definições Avançadas para o menu Mais opções.

### Corrigido:
- Melhorámos a UI/UX do nosso tratamento de erros.
- Melhorámos a experiência de transferência dos parâmetros Sapling.
- Corrigimos alguns problemas e falhas reportados pelos utilizadores.


## [2.4.11 (1418)] - 2025-12-18

### Adicionado:
- Atualizámos os ícones dos ativos e redes do Swap/Pay.

### Alterado:
- Introduzimos um novo estado Pendente para os fluxos de transação, que substituiu os nossos estados de Erro.
- Fizemos atualizações de ícones e texto.
- Melhorámos o nosso tratamento de erros.
- Movemos a Conversão de Moeda das Definições Avançadas para o menu Mais opções.

### Corrigido:
- Melhorámos a UI/UX do nosso tratamento de erros.
- Melhorámos a experiência de transferência dos parâmetros Sapling.
- Corrigimos alguns problemas e falhas reportados pelos utilizadores.


## [2.4.10 (1413)] - 2025-12-16

### Adicionado:
- Atualizámos os ícones dos ativos e redes do Swap/Pay.

### Alterado:
- Introduzimos um novo estado Pendente para os fluxos de transação, que substituiu os nossos estados de Erro.
- Fizemos atualizações de ícones e texto.
- Melhorámos o nosso tratamento de erros.
- Movemos a Conversão de Moeda das Definições Avançadas para o menu Mais opções.

### Corrigido:
- Melhorámos a UI/UX do nosso tratamento de erros.
- Melhorámos a experiência de transferência dos parâmetros Sapling.
- Corrigimos alguns problemas e falhas reportados pelos utilizadores.


## [2.4.10 (1412)] - 2025-12-16

### Adicionado:
- Atualizámos os ícones dos ativos e redes do Swap/Pay.

### Alterado:
- Introduzimos um novo estado Pendente para os fluxos de transação, que substituiu os nossos estados de Erro.
- Fizemos atualizações de ícones e texto.
- Melhorámos o nosso tratamento de erros.
- Movemos a Conversão de Moeda das Definições Avançadas para o menu Mais opções.

### Corrigido:
- Melhorámos a UI/UX do nosso tratamento de erros.
- Melhorámos a experiência de transferência dos parâmetros Sapling.
- Corrigimos alguns problemas e falhas reportados pelos utilizadores.

## [2.4.9 (1387)] - 2025-12-09

### Adicionado:
- Adicionámos melhorias no tratamento de erros para os erros mais frequentes do Zashi, para te ajudar a compreendê-los e resolvê-los.
- Adicionámos uma opção que te permite ativar a proteção de IP por Tor no fluxo de Restauração.

### Alterado:
- Adicionámos um botão Swap que leva diretamente aos swaps.
- Melhorámos o desempenho da Conversão de Moeda.
- Movemos a funcionalidade Pagar com Flexa para o menu Mais opções.
- Removemos a integração com o Coinbase Onramp.
- Melhorámos também a experiência de Repor o Zashi.

### Corrigido:
- Detetámos e corrigimos vários problemas reportados pelos utilizadores.
- Implementámos uma funcionalidade que te permite obter os dados das transações

## [2.4.9 (1386)] - 2025-12-04

### Adicionado:
- Adicionámos melhorias no tratamento de erros para os erros mais frequentes do Zashi, para te ajudar a compreendê-los e resolvê-los.
- Adicionámos uma opção que te permite ativar a proteção de IP por Tor no fluxo de Restauração.

### Alterado:
- Adicionámos um botão Swap que leva diretamente aos swaps.
- Melhorámos o desempenho da Conversão de Moeda.
- Movemos a funcionalidade Pagar com Flexa para o menu Mais opções.
- Removemos a integração com o Coinbase Onramp.
- Melhorámos também a experiência de Repor o Zashi.

### Corrigido:
- Detetámos e corrigimos vários problemas reportados pelos utilizadores.
- Implementámos uma funcionalidade que te permite obter os dados das transações

## [2.4.8 (1321)] - 2025-11-20

### Alterado:
- Atualizámos as funcionalidades Swap e Pay para usarem endereços protegidos em vez de transparentes. Escudos para cima!

### Corrigido:
- Corrigimos também alguns problemas reportados pelos utilizadores.


## [2.4.8 (1319)] - 2025-11-14

### Alterado:
- Atualizámos as funcionalidades Swap e Pay para usarem endereços protegidos em vez de transparentes. Escudos para cima!

### Corrigido:
- Corrigimos também alguns problemas reportados pelos utilizadores.

## [2.4.7 (1309)] - 2025-11-05

### Adicionado:
- Adicionámos feedback tátil para ações importantes do utilizador.

### Alterado:
- Adicionámos um novo servidor à lista de servidores do Zashi.
- Aumentámos o prazo do swap para evitar reembolsos prematuros.

### Corrigido:
- Fizemos um conjunto de correções de erros, refatorações e melhorias de UX/UI.
- Implementámos um mecanismo para descobrir fundos de transações TEX falhadas.

## [2.4.6 (1279)] - 2025-10-27

### Corrigido:
- Corrigimos um problema com o swap de valores maiores que resultava num erro. Faz swap à vontade!

## [2.4.5 (1276)] - 2025-10-24

### Corrigido:
- Corrigimos um problema de saldo e proteção que afetava as carteiras de alguns utilizadores.

## [2.4.5 (1274)] - 2025-10-23

### Corrigido:
- Corrigimos um problema de saldo e proteção que afetava as carteiras de alguns utilizadores.

## [2.4.4 (1265)] - 2025-10-20

### Alterado:
- Implementámos a atualização automática dos estados de swap/pagamento; já não é preciso abrir cada um para ver as alterações.
- Removemos o logótipo da Keystone do código QR; ninguém precisa de saber que tens uma.
- Resolvemos o requisito de tamanho da biblioteca da Google.

### Corrigido:
- Corrigimos um problema com uma transação recebida que ficava eternamente pendente.
- Corrigimos um problema com um falso positivo de erro num envio TEX.
- Corrigimos um problema com a app a falhar durante a estimativa da altura de bloco.
- Fizemos também mais algumas correções de erros.

## [2.4.3 (1250)] - 2025-10-13

### Corrigido:
- Correções de erros
- Atualizações de UI/UX

## [2.4.2 (1248)] - 2025-10-07

### Alterado:
- Removemos os servidores lwd da lista de servidores porque deixarão de ser suportados em breve.

### Corrigido:
- Corrigimos alguns problemas de UI.

## [2.4.1 (1240)] - 2025-10-03

### Adicionado:
- A funcionalidade Swap para ZEC que estavas à espera! Suportada pela Near Intents.
- Usa o Zashi para fazer swap de qualquer criptomoeda suportada para Zcash.
- Deposita fundos usando qualquer uma das tuas carteiras favoritas.
- Recebe ZEC no Zashi e protege-o.
- Vê as transações recebidas mais depressa com a deteção na mempool.
- Tem o teu troco confirmado mais depressa com 3 confirmações.
- Mais correções de erros e atualizações de design

## [2.4.1 (1239)] - 2025-10-02

### Adicionado:
- A funcionalidade Swap para ZEC que estavas à espera! Suportada pela Near Intents.
- Usa o Zashi para fazer swap de qualquer criptomoeda suportada para Zcash.
- Deposita fundos usando qualquer uma das tuas carteiras favoritas.
- Recebe ZEC no Zashi e protege-o.
- Vê as transações recebidas mais depressa com a deteção na mempool.
- Tem o teu troco confirmado mais depressa com 3 confirmações.
- Mais correções de erros e atualizações de design

## [2.4.0 (1225)] - 2025-09-30

### Adicionado:
- A funcionalidade Swap para ZEC que estavas à espera! Suportada pela Near Intents.
- Usa o Zashi para fazer swap de qualquer criptomoeda suportada para Zcash.
- Deposita fundos usando qualquer uma das tuas carteiras favoritas.
- Recebe ZEC no Zashi e protege-o.
- Vê as transações recebidas mais depressa com a deteção na mempool.
- Tem o teu troco confirmado mais depressa com 3 confirmações.

## [2.4.0 (1223)] - 2025-09-30

### Adicionado:
- A funcionalidade Swap para ZEC que estavas à espera! Suportada pela Near Intents.
- Usa o Zashi para fazer swap de qualquer criptomoeda suportada para Zcash.
- Deposita fundos usando qualquer uma das tuas carteiras favoritas.
- Recebe ZEC no Zashi e protege-o.
- Vê as transações recebidas mais depressa com a deteção na mempool.
- Tem o teu troco confirmado mais depressa com 3 confirmações.

## [2.3.0 (1160)] - 2025-09-15

### Adicionado:
- CrossPay com a Near Intents
- Usa ZEC protegido para fazer pagamentos entre redes com o Zashi e a Near Intents.
- Acessível através do novo botão Pay no ecrã inicial.

## [2.3.0 (1159)] - 2025-09-15

### Adicionado:
- CrossPay com a Near Intents
- Usa ZEC protegido para fazer pagamentos entre redes com o Zashi e a Near Intents.
- Acessível através do novo botão Pay no ecrã inicial.

## [2.2.1 (1121)] - 2025-08-29

### Adicionado:
- Swap de ZEC com o Zashi:
- Faz swap de ZEC protegido para qualquer criptomoeda suportada com a integração Near Intents.
- O Zashi é uma carteira só de ZEC, por isso vais precisar de um endereço de carteira válido para o ativo para o qual estás a fazer swap.

## [2.2.0 (1120)] - 2025-08-27

### Adicionado:
- Swap de ZEC com o Zashi:
- Faz swap de ZEC protegido para qualquer criptomoeda suportada com a integração Near Intents.
- O Zashi é uma carteira só de ZEC, por isso vais precisar de um endereço de carteira válido para o ativo para o qual estás a fazer swap.

## [2.1.0 (999)] - 2025-08-06

### Adicionado:
- O cliente Tor integrado do Zashi pode agora ser usado para:
- Submeter transações ZEC
- Obter dados das transações
- Conectar-se a APIs de terceiros (por exemplo, NEAR, em breve!)
- Obter taxas de câmbio ZEC-USD
- Se o Tor estiver disponível na tua região, recomendamos vivamente que o actives nas Definições Avançadas.

### Alterado:
- Adicionámos explicações sobre os endereços.
- Melhorámos o tempo de arranque da app.
- Corrigimos erros na leitura de ZIP 321, no fluxo Enviar novamente e outros problemas de UI/UX.

## [2.1.0 (997)] - 2025-07-30

### Adicionado:
- O cliente Tor integrado do Zashi pode agora ser usado para:
- Submeter transações ZEC
- Obter dados das transações
- Conectar-se a APIs de terceiros (por exemplo, NEAR)
- Obter taxas de câmbio ZEC-USD
- Se o Tor estiver disponível na tua região, recomendamos vivamente que o actives.
- Nota: a Conversão de Moeda agora só funciona com o Tor ativado.

### Alterado:
- Adicionámos explicações sobre os endereços.
- Melhorámos o tempo de arranque da app.
- Corrigimos erros na leitura de ZIP 321, no fluxo Enviar novamente e outros problemas de UI/UX.

## [2.1.0 (996)] - 2025-07-29

### Adicionado:
- O cliente Tor integrado do Zashi pode agora ser usado para:
- Submeter transações ZEC
- Obter dados das transações
- Conectar-se a APIs de terceiros (por exemplo, NEAR)
- Obter taxas de câmbio ZEC-USD
- Se o Tor estiver disponível na tua região, recomendamos vivamente que o actives.
- Nota: a Conversão de Moeda agora só funciona com o Tor ativado.

### Alterado:
- Adicionámos explicações sobre os endereços.
- Melhorámos o tempo de arranque da app.
- Corrigimos erros na leitura de ZIP 321, no fluxo Enviar novamente e outros problemas de UI/UX.

## [2.0.5 (974)] - 2025-06-25

### Corrigido:
- Corrigimos uma falha quando a inicialização do Tor falha

## [2.0.4 (973)] - 2025-06-16

### Adicionado:
- Adicionámos um acionador para obter uma taxa de conversão atualizada quando navegas para o ecrã Enviar.

### Alterado:
- Unificámos o comportamento dos separadores de grupo e decimal para evitar gastos excessivos.
- Atualizámos os nossos ícones de Enviar e Receber.
- Atualizámos o texto no ecrã Receber.
- Atualizámos a animação no ecrã Enviar.

### Corrigido:
- Corrigimos o problema com a obtenção da taxa de conversão USD e tornámo-la mais fiável.

## [2.0.3 (965)] - 2025-05-19

### Alterado:
- O Zashi já não inclui recetores transparentes nos Endereços Unificados.
- O ecrã Receber mostra agora um UA rotativo, apenas protegido, que é gerado de novo sempre que abres o ecrã Receber.
- Todas as transações enviadas para os teus diferentes Endereços Protegidos rotativos farão parte de um único saldo de carteira sob a mesma frase semente.
- As carteiras e casas de câmbio que não suportam o envio de fundos para recetores protegidos exigirão um endereço transparente.

## [2.0.2 (962)] - 2025-05-14

### Alterado:
- Ao introduzir um valor em USD, arredondamos automaticamente o valor em Zatoshi para os 5000 Zatoshi mais próximos, para evitar criar notas de pó (dust) não gastáveis na tua carteira.
- Atualizámos a posição dos botões primário e secundário para seguir as melhores práticas de UX.
- Atualizámos o design do ecrã Receber.
- Atualizámos os ícones dos ecrãs Enviar e Receber em toda a app com base no teu feedback.
- Melhorámos o texto em vários sítios.
- Fizemos também alguns outros ajustes de UI.

### Corrigido:
- Fizemos algumas correções de erros.

## [2.0.1 (941)] - 2025-04-29

### Adicionado:
- O Zashi 2.0 chegou!
- O novo Widget de Estado da Carteira ajuda-te a navegar no Zashi com facilidade e a obter mais informação ao tocar.

### Alterado:
- Ecrã inicial redesenhado e navegação da app simplificada.
- Saldos redesenhados num novo componente Disponível no ecrã Enviar.
- Fluxo de Restauração renovado.
- Cria a carteira com um toque! O novo fluxo de Cópia de Segurança da Carteira foi movido para o momento em que a tua carteira recebe os primeiros fundos.
- O Firebase Crashlytics é totalmente opcional. Ajuda-nos a melhorar o Zashi, ou não, a escolha é tua.
- Ler um código QR ZIP 321 abre agora o Zashi!

## [2.0.0 (934)] - 2025-04-25

### Adicionado:
- O Zashi 2.0 chegou!
- O novo Widget de Estado da Carteira ajuda-te a navegar no Zashi com facilidade e a obter mais informação ao tocar.

### Alterado:
- Ecrã inicial redesenhado e navegação da app simplificada.
- Saldos redesenhados num novo componente Disponível no ecrã Enviar.
- Fluxo de Restauração renovado.
- Cria a carteira com um toque! O novo fluxo de Cópia de Segurança da Carteira foi movido para o momento em que a tua carteira recebe os primeiros fundos.
- O Firebase Crashlytics é totalmente opcional. Ajuda-nos a melhorar o Zashi, ou não, a escolha é tua.
- Ler um código QR ZIP 321 abre agora o Zashi!

## [1.5.2 (932)] - 2025-04-23

### Adicionado:
- Adicionámos uma opção para os utilizadores da Play Store recusarem a partilha de relatórios de falhas via Firebase Crashlytics. Podes encontrar esta nova definição em Definições Avançadas -> Relatórios de Falhas.

## [1.5.2 (929)] - 2025-04-09

### Corrigido
- Versão de correção de erros 1.5!
- Corrigimos um problema de migração que afetava alguns utilizadores na versão 1.5 da app.
- Removemos também o ecrã redundante de Aviso de Segurança que informava incorretamente o utilizador de que os relatórios
  de falhas não estavam incluídos na versão FOSS do Zashi Android.


## [1.5.2 (926)] - 2025-04-03

### Corrigido:
- Versão de correção de erros 1.5!
- Corrigimos um problema de migração que afetava alguns utilizadores na versão 1.5 da app.

## [1.5.1 (925)] - 2025-03-31

### Adicionado:
- Recuperação de Fundos Transparentes - o Zashi pode agora ajudar-te a recuperar fundos de carteiras totalmente transparentes, como a Ledger. Recomendamos importar a frase de recuperação da tua carteira de hardware transparente para uma carteira de hardware Keystone e, depois, emparelhá-la com o Zashi através da integração Keystone.

### Corrigido:
- Corrigimos um problema de longa data na árvore de compromissos de notas que afetava um pequeno número de utilizadores. O Zashi consegue agora permitir que os fundos presos sejam gastos.

## [1.5 (923)] - 2025-03-27

### Adicionado:
- Recuperação de Fundos Transparentes - o Zashi pode agora ajudar-te a recuperar fundos de carteiras totalmente transparentes, como a Ledger. Recomendamos importar a frase de recuperação da tua carteira de hardware transparente para uma carteira de hardware Keystone e, depois, emparelhá-la com o Zashi através da integração Keystone.

### Corrigido:
- Corrigimos um problema de longa data na árvore de compromissos de notas que afetava um pequeno número de utilizadores. O Zashi consegue agora permitir que os fundos presos sejam gastos.

## [1.4 (876)] - 2025-03-04

### Adicionado
- Exporta o histórico de transações do último ano com a nova funcionalidade Exportar Ficheiro Fiscal.
- Marca transações e adiciona notas privadas.
- Filtra por transações Recebidas, Enviadas, Mensagens, Notas e Marcadas.
- Descarrega o Zashi a partir da F-Droid e do GitHub.

### Alterado
- Descobre o Histórico de Transações redesenhado!
- Acede à Keystone a partir do ecrã Integrações.
- Desfruta de uma experiência de assinatura de transações melhorada.

### Corrigido
- Acabaram-se as falhas no Envio com a Keystone, corrigimo-lo.
- Corrigimos também a funcionalidade Ficheiro Fiscal.

## [1.4 (873)] - 2025-03-03

### Adicionado
- Exporta o histórico de transações do último ano com a nova funcionalidade Exportar Ficheiro Fiscal.
- Marca transações e adiciona-lhes notas privadas.
- Filtra por transações Recebidas, Enviadas, Mensagens, Notas e Marcadas.
- Descarrega o Zashi Android a partir da F-Droid e do GitHub.

### Alterado
- Descobre o Histórico de Transações completamente redesenhado!
- Acede à Keystone a partir do ecrã Integrações.
- Desfruta de uma experiência de assinatura de transações melhorada.

### Corrigido
- Acabaram-se as falhas no Envio com a Keystone, corrigimos o problema!

## [1.3.3 (839)] - 2025-01-23

### Alterado
- Refatorámos a lógica da imagem do código QR para funcionar com o componente mais recente ZashiQr.
- As cores da imagem do código QR no ecrã SignTransaction são agora iguais em ambos os temas de cor, para melhorar
  a leitura pelo dispositivo Keystone.
- Melhorámos a lógica do progresso de sincronização de blocos para devolver uma percentagem incompleta caso o Synchronizer
  ainda esteja no estado SYNCING.
- Atualizámos o Keystone SDK para a versão 0.7.10, que traz uma melhoria significativa na leitura de códigos QR.

### Corrigido
- Corrigimos a lógica do popup de Desconectado para os casos em que a app está em segundo plano.
- Resolvemos também um problema com a pilha de navegação da app que não era limpa depois de clicar no botão Ver
  Transações.

## [1.3.2 (829)] - 2025-01-10

### Alterado
- O Zashi mostra agora a versão escura dos códigos QR no tema escuro
- Melhorámos o leitor de códigos QR para responder mais depressa
- Refatorámos os ecrãs de Envio para funcionarem melhor para ti

### Corrigido
- Corrigimos também a forma como o Zashi trata os endereços dentro dos códigos QR

## [1.3.1 (822)] - 2025-01-07

### Corrigido
- Corrigimos um erro na funcionalidade Coinbase Onramp que afetava os utilizadores que faziam compras com a sua conta Coinbase.
  Passamos agora um endereço transparente correto para a Coinbase e o teu ZEC é enviado diretamente para a tua carteira Zashi, em vez
  da tua conta Coinbase.

## [1.3 (812)] - 2024-12-19

### Adicionado
A integração Zashi + Carteira de Hardware Keystone está ativa!
- Conecta a tua carteira Keystone ao Zashi.
- Assina uma transação com a tua carteira Keystone.
- Inclui suporte para ZEC tanto protegido como transparente.

## [1.2.3 (799)] - 2024-11-26

### Adicionado
- Finalmente chegou! A integração Flexa ao teu dispor!
- Paga com a Flexa em comerciantes suportados nos EUA, Canadá e El Salvador.
- Está à tua espera nas Definições do Zashi.

## [1.2.2 (789)] - 2024-11-18

### Adicionado
- Olá! Ensinámos o Zashi a falar espanhol!
- Adotámos a versão 2.2.6 do SDK, que deverá ajudar a acelerar o envio de várias transações.
- Implementámos a encriptação e o armazenamento remoto do Livro de Endereços!
- Adicionámos a autenticação do dispositivo ao iniciar a app.
- Adicionámos um ecrã de progresso animado e novos ecrãs de sucesso e falha.

### Alterado
- Tornámos os ecrãs de Definições e de estado mais bonitos.
- Diz-nos o que achas do Zashi com a funcionalidade melhorada de Enviar Comentários.

### Corrigido
- Corrigimos o comportamento do ícone de proteção no Histórico de Transações.

## [1.2.1 (760)] - 2024-10-22

### Adicionado
- Cansado de copiar e colar endereços? Adicionámos uma funcionalidade de Livro de Endereços!
- Apresentamos a funcionalidade "Solicitar ZEC": cria facilmente um pedido de pagamento e partilha-o como um código QR!

### Alterado
- O separador Receber recebeu algum carinho — redesenhámo-lo com base no teu feedback.
- Ajustámos o formulário de Envio.
- Atualizámos o histórico de transações para simplificar a tua experiência.
- E não é tudo — a UI de Leitura também foi redesenhada.
- Fizemos também muitos outros pequenos ajustes e correções de UI/UX pelo caminho. Aproveita!

## [1.2 (739)] - 2024-09-27

### Alterado
- Adotámos a versão snapshot 2.2.5 do Zcash SDK, que inclui uma correção para problemas de sincronização de blocos causados por uma verificação incorreta do componente da altura de bloco.

## [1.2 (735)] - 2024-09-20

### Adicionado:
- Todos os diálogos de erro do Zashi têm agora um botão Reportar que preenche previamente o rastreio de erro num cliente de e-mail selecionado.

### Alterado:
- O campo de Mensagem no ecrã Enviar foi atualizado para incluir uma tecla Enter no teclado virtual e para aplicar maiúscula automática no início de cada frase ou nova linha.

### Corrigido:
- Corrigimos a funcionalidade Enviar Comentários e tornámo-la compatível com mais clientes de e-mail.

## [1.2 (731)] - 2024-09-16

### Adicionado
- Adicionámos uma funcionalidade experimental que te permite comprar ZEC com a integração Coinbase Onramp - encontra-la nas Definições Avançadas.
- Não precisas de continuar a adivinhar qual o servidor com melhor desempenho. Adicionámos uma troca dinâmica de servidor, que identifica os servidores com melhor desempenho para ti.
- Melhorámos a UX das transações por enviar. O SDK verifica agora se existem transações por enviar e tenta reenviá-las.
- Adicionámos também suporte para o Android 15.

### Alterado
- Atualizámos a UI das nossas Definições.

## [1.2 (729)] - 2024-09-13

### Adicionado
- Adicionámos uma funcionalidade experimental que te permite comprar ZEC com a integração Coinbase Onramp - encontra-la nas Definições Avançadas.
- Não precisas de continuar a adivinhar qual o servidor com melhor desempenho. Adicionámos uma troca dinâmica de servidor, que identifica os servidores com melhor desempenho para ti.
- Melhorámos a UX das transações por enviar. O SDK verifica agora se existem transações por enviar e tenta reenviá-las.
- Adicionámos também suporte para o Android 15.

### Alterado
- Atualizámos a UI das nossas Definições.

## [1.1.7 (718)] - 2024-09-06

### Adicionado
- Adicionámos a conversão de moeda ZEC/USD ao Zashi sem comprometer o teu endereço IP.
- Podes agora ver os teus saldos e introduzir valores de transação tanto em USD como em ZEC.

### Alterado
- Adotámos a versão mais recente 2.2.0 do Zcash SDK, que traz o suporte para endereços TEX ZIP 320, a funcionalidade de conversão de moeda que obtém a taxa de câmbio ZEC/USD por Tor e o suporte para restaurar o histórico completo de carteiras apenas transparentes.

### Corrigido
- Reativámos o teste de capturas de ecrã da app depois de termos abandonado os componentes AppCompat.

## [1.1.6 (712)] - 2024-09-04

### Adicionado
- Adicionámos a conversão de moeda ZEC/USD ao Zashi sem comprometer o teu endereço IP.
- Podes agora ver os teus saldos e introduzir valores de transação tanto em USD como em ZEC.

### Alterado
- Adotámos a versão mais recente 2.2.0 do Zcash SDK, que traz o suporte para endereços TEX ZIP 320, a funcionalidade de conversão de moeda que obtém a taxa de câmbio ZEC/USD por Tor e o suporte para restaurar o histórico completo de carteiras apenas transparentes.

### Corrigido
- Reativámos o teste de capturas de ecrã da app depois de termos abandonado os componentes AppCompat.

## [1.1.5 (706)] - 2024-08-09

### Alterado
- Adotámos a versão mais recente 2.1.3 do Zcash SDK, que melhora significativamente a velocidade de sincronização de blocos.
- Melhorámos também a lógica para obter transações transparentes.

## [1.1.4 (700)] - 2024-07-23

### Adicionado
- Adicionámos a informação Novidades ao ecrã Acerca de.
- Protegemos a cópia de informação sensível para a área de transferência do dispositivo, ocultando-a da confirmação visual do sistema.

### Alterado
- Adicionámos um ecrã com dicas de sincronização para uma Restauração bem-sucedida.
- Atualizámos a UI do ecrã Acerca de.

### Corrigido
- Corrigimos a velocidade e a fiabilidade da leitura de códigos QR.
- Corrigimos a UI no ecrã de Comentários, para que o botão Enviar deixe de ficar escondido pelo teclado.
- Melhorámos também o tratamento das alterações de configuração do Android.
