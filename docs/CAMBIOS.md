# MatraMotors — registro de cambios

## 0.1.0-beta — 12/11/2026 (acceso anticipado)
**MatraMotors pasa a ser un solo plugin**: coches, barcos y trenes en el mismo jar. Antes eran MATRAMOTORS 2.6.1 y Matrains 1.1.0; las versiones empiezan de nuevo porque todo lo anterior eran pruebas previas.
- El nombre ya no va en mayúsculas: **MatraMotors**.
- Los trenes (antes Matrains) viven dentro: `/tren`, `/trenes`, `/matrains` siguen igual. El menú único `/vehiculos` los lee directamente.
- Config nueva: los trenes tienen su propio archivo, `config-trenes.yml`, junto a `config.yml` (coches y barcos).
- Al arrancar la primera vez copia solo los datos de `plugins/Matrains/` (trenes, estaciones, luz, redes y su config). No borra nada: la carpeta vieja se puede quitar después.
- Si queda un `Matrains.jar` suelto, MatraMotors lo avisa en la consola y no arranca los trenes (se borra y listo).
- Las marcas internas de objetos y trenes (`coches:` y `matrains:`) no cambian, así que los vehículos, llaves y bidones que ya existen siguen valiendo.

---
Historial anterior (versiones de pruebas):

## Coches y barcos (MATRAMOTORS)

Versionado: MAYOR.MENOR.PARCHE
- PARCHE: arreglos o ajustes que no cambian cómo se juega
- MENOR: algo nuevo que no rompe lo que había
- MAYOR: cambio gordo que obliga a hacer algo o rompe lo anterior

## 2.6.1 — 12/11/2026
- Arreglado: «/coche reparar» y «/barco reparar» reparaban y cobraban bien pero luego daban un error en el chat (fallo al escribir el mensaje del coste) y no salía la confirmación. Ahora sale el mensaje normal.

## 2.6.0 — 11/11/2026
- **Menú único de vehículos: /vehiculos** (también /garaje; /coche, /barco y /tren abren lo mismo). `/vehiculos mis` va directo a «Mis vehículos».
- «Mis vehículos» ahora lo enseña todo en una ventana, en tres filas:
  - **Tuyos**: todos los coches y barcos a tu nombre, aunque no lleves la llave encima (con seguro y dónde están). Con la llave: seguro, grúa y copia; sin ella: GPS hasta el coche.
  - **Prestados**: coches de otros de los que llevas la llave (dueño, dónde está y GPS).
  - **Trenes** (si está Matrains): tus trenes en las vías y los prestados (llevas su llave), con energía, modo automático y luz pagada; clic abre su panel. Botón al menú de trenes.
- El botón suelto de «Coches prestados» del menú principal desaparece (está dentro de «Mis vehículos»). `/coche prestados` sigue funcionando.

## 2.5.0 — 11/11/2026
- **Bidón de gasolina (10 L)**: clic derecho a cualquier surtidor andando (sin mayúsculas) abre la tienda de la gasolinera: comprar 1, 4 o 16 bidones al precio del litro de ese surtidor, o rellenar los bidones empezados pagando solo lo que falta.
- Con el bidón en la mano, clic derecho a un coche, una embarcación o una locomotora diésel (Matrains 1.0.1) para echarle gasolina. Si sobra, el bidón se queda empezado (la barra del lore marca lo que queda). Vale para el vehículo de cualquiera: para rescatar a un amigo tirado.
- Tiene el aspecto del cubo de lava pero no se coloca, no se fabrica con él ni se puede vender a aldeanos.
- El botón «⛽ Gasolina» del menú lo explica.

## 2.4.0 — 11/11/2026
- Gasolina a precio único: la náutica cuesta lo mismo que la de tierra (`gasolina.suplemento-nautico-euros: 0`). El Taxi Boat mantiene su tarifa de taxi.
- Botón «🚆 Trenes» en el menú principal (/coche, /barco): abre el menú de Matrains.

## 2.3.0 — 11/11/2026
**Coches rehechos, con más forma**
- Las piezas de los modelos ya pueden ir inclinadas o giradas: parabrisas y lunetas tumbados, capós caídos, esquinas achaflanadas, aletas y ruedas redondeadas (cubierta en cruz + rombo y llanta en rombo).
- Rehechos los 7 coches: Tartana, Turismo, Deportivo, Furgoneta, Motocarro, Quad y Taxi. Los que ya tenéis cambian solos de aspecto (conservan colores y extras).
- En el Quad y el Motocarro el conductor va sentado más alto, encima del sillín.

**Más skins**
- Coches: 2 estilos nuevos por modelo (Tartana: Bicolor y Rally; Turismo: Franja y Techo negro; Deportivo: Franjas de carreras y Bitono; Furgoneta: Reparto y Camper; Motocarro: Toldo y Bitono; Quad: Carreras y Campo; Taxi: Anuncio y Doble franja). La mitad de los coches nuevos salen con el estilo de siempre.
- Embarcaciones: 2 estilos nuevos por modelo (Pedalo: Cisne y Toldo a rayas; Moto de agua: Carreras y Cebra; Barco: Pesquero y Lujo; Taxi Boat: Clásico y Nocturno; La Goma: Salvamento y Camuflaje; Patrullero: Costera y Rescate; Hipersónica: Carreras y Cromada).
- El estilo sale en la descripción del objeto, debajo del color.

**Banderas de La Goma**
- Nuevas: Israel y Marruecos, más detalladas que las de antes. Ahora hay cinco banderas posibles; el 40 % de las gomas sigue saliendo sin bandera.

## 2.3.0 — 11/11/2026
**Vehículos rehechos, menos cuadriculados**
- Las piezas ya pueden ir giradas: esquinas achaflanadas, parabrisas y lunetas inclinados, capós en pendiente y ruedas casi redondas.
- Rehechos todos los coches: Tartana, Turismo, Deportivo, Furgoneta, Motocarro, Quad y Taxi.

**Dos skins más por vehículo** (salen al azar al comprar; la de siempre sigue siendo la más común)
- Tartana: Retro y Safari · Turismo: Rally y Tuning · Deportivo: Carbono y Le Mans · Furgoneta: Food truck y Mudanzas · Motocarro: Frutero y Butano · Quad: Desierto y Cross · Taxi: Franja larga y Clásico.
- Pedalo: Cisne y Toldo a rayas · Moto de agua: Carreras y Cebra · Barco: Pesquero y Lujo · Taxi Boat: Clásico y Nocturno · La Goma: Salvamento y Camuflaje · Patrullero: Costera y Rescate · Hipersónica: Carreras y Cromada.

**Banderas de La Goma**: se suman la de Israel y la de Marruecos a las tres que había (sin bandera el 40 %).

Los vehículos ya comprados conservan su skin; las nuevas solo salen en los que se compren a partir de ahora.

## 2.2.1 — 11/11/2026
- Arreglado: las embarcaciones no se movían con la base nueva (un soporte de armadura sin gravedad se queda clavado). Ahora flotan con la gravedad a cero en vez de quitada.

## 2.2.0 — 11/11/2026
**Vehículos sin vagoneta a la vista**
- La base de coches y embarcaciones ya no es una vagoneta (el juego la dibuja aunque sea invisible): ahora es un soporte de armadura invisible y diminuto. Solo se ve la carrocería.
- Los vehículos que ya estaban puestos en el mundo se cambian solos al cargar su zona, con su matrícula, gasolina, estado, colores y maletero.
- El conductor queda a la misma altura que antes (`base.escala`, por defecto 0.19) y la base cae igual que la vagoneta (`base.gravedad`, 0.04).
- Clic derecho, mayúsculas + clic derecho y mayúsculas + golpe funcionan igual, también sobre la base.

**Moto de agua nueva**
- Casco con proa en punta, cubierta, sillín largo con respaldo, capó en dos alturas con parabrisas y faro, manillar con puños y plataforma de popa.
- Estilos: Lisa, Franja (banda en los costados) y Dos tonos (casco de abajo en el segundo color). El pasajero va sentado en el sillín, detrás del conductor.

## 2.1.0 — 11/11/2026
**Seguros sin dinero de la nada**
- Las primas de todos los seguros van a un fondo común de las aseguradoras (`seguros.yml` → `fondo`). Las indemnizaciones por atropello salen de ese fondo; si no llega, pone el resto el titular de la póliza (lo que pueda). El fondo empieza a cero y se llena con cada prima o renovación.
- Como mucho un parte indemnizado por pareja conductor–perjudicado cada 10 minutos, y un tope de 2.000 Ⓜ al día por perjudicado (`seguros.indemnizacion.espera-minutos` y `tope-diario`).

**Firme, clima y chunks**
- Los coches van a tope por asfalto (hormigón, caminos, piedra pulida, ladrillo, losas y escaleras), al 60 % por tierra o hierba y al 40 % por arena, grava o nieve. El Quad solo nota un tercio de la pérdida. Al pasar a un firme peor se pierde velocidad poco a poco, sin frenazos (`suelo.*`).
- Clima real: con lluvia los coches frenan a la mitad y giran algo peor; con tormenta los barcos van al 80 % y La Goma vuelca antes (`clima.*`).
- Si a dos segundos por delante el mundo aún no está generado, la velocidad se limita a 10 bloques/s para que el servidor no se ahogue (`chunks.velocidad-sin-generar`).

## 2.0.0 — 11/11/2026
**Matracar pasa a llamarse MATRAMOTORS** (cambio gordo). Al arrancar copia sola la carpeta `plugins/Matracar` a `plugins/MATRAMOTORS`: coches, matrículas, seguros, carnés, tickets y gasolineras siguen igual. Hay que quitar `Matracar.jar` (los dos a la vez chocan). El panel de admin lo sigue reconociendo como Matracar.

**Embarcaciones** (`/coche` → Comprar vehículo, fila de abajo)
- 7 modelos: El Pedalo (sin motor, 1 plaza), Moto de agua (salta en rampas), Barco, Taxi Boat (8 plazas, taxímetro), La Goma (la que más carga, frágil, vuelca si giras a tope muy rápido y se desinfla con poca vida), Patrullero (el más resistente) y La Hipersónica (la más rápida).
- Skins al azar como los coches: estilo (toldo, tobogán, franjas, dos tonos, nº de motores…) y colores; La Goma además bandera, lona, refuerzo y parches.
- Se echan al agua apuntando al agua. Flotan, no suben a tierra (al llegar a la orilla se paran; si llegan rápido, golpe), no corren sobre hielo, remontan corrientes y cascadas despacio y caer al agua no hace daño.
- Pasajeros: clic derecho en un vehículo con conductor para subirte de pasajero si hay plazas (también en el coche Taxi).
- Sin grúa: si se queda sin gasolina, guárdala (mayúsculas + golpe) y vete a una gasolinera náutica.
- Matrícula en los costados. Mismos seguros (Mutua Central y Aseguradora Norte), llaves, venta, desguace y taller que los coches.
- Solo en `world` (`mundos-permitidos` en el config). Igual para los coches.

**Gasolineras**
- Nuevas: de 2x2 a 5x5 del mismo bloque (`/gasolinera crear` mirando al suelo). Se reposta tocando cualquiera de sus bloques. Las antiguas de 1 bloque siguen funcionando; `/gasolinera lista` avisa de que conviene rehacerlas.
- Náuticas: el suelo va bajo el agua (hasta 2 bloques de agua encima). Gasolina 0,15 €/L más cara, nombres inventados (Marina Matraca, Varadero Norte…). No se puede construir encima, ni hacer columnas de burbujas, ni poner o que se forme hielo a 5 bloques.

**Daños proporcionales** (coches y barcos)
- Choque de frente: nada por debajo del 35 % de la velocidad máxima; a tope, 40 %. Un golpe por segundo como mucho (rozar un bordillo ya no destroza el coche).
- Escaleras y losas son rampas: ni choque ni caída. Caídas: nada hasta 3 bloques, 6 % por bloque más (20 bloques = siniestro).
- Cada vehículo tiene su resistencia (`resistencia` y `vida` en el config).

**Coche Taxi y taxímetro**
- Nuevo coche **Taxi** (11.000 Ⓜ, 3 plazas): blanco con la franja amarilla y luz en el techo (verde libre, roja ocupado).
- `/taximetro` lo enciende o apaga (no se puede apagar con un cliente: `/taximetro terminar` cobra lo recorrido). `/taximetro tarifa <50-200>` pone tu tarifa en % de la oficial.
- Tarifa de ejemplo de un taxi de gran ciudad (2026) (1 km = 10 bloques; tarifa 2 de noche y fines de semana); Taxi Boat +25 %.
- El pasajero elige destino en una ventana (sus casas, warps, un jugador o unos bloques), ve el precio cerrado y se le retiene el dinero. Al llegar cobra el dueño del taxi; si se baja antes, paga lo recorrido; si vuelve al mismo taxi antes de 3 min, sigue la carrera. Si el servidor se cae con dinero retenido, se devuelve al arrancar.

**Arreglos**
- El arranque ya no engancha la parte de extras antes de cargar la configuración.

## 1.2.2 — 05/11/2026
- Arreglado: a quien tiene activada la ordenación automática de ChestSort no le salía el botón **Comprar coche** en `/coche` (lo tapaba "Coches prestados"). ChestSort revolvía el menú un instante y el botón nuevo se colocaba encima del concesionario.

## 1.2.1 — 05/11/2026
Menú de `/coche` más claro (no cambia cómo se juega).
- El botón del concesionario se llama ahora **Comprar coche** (sigue siendo la vagoneta, el primero de la segunda fila).
- **Coches prestados** tiene icono de etiqueta (antes era el mismo gancho que "Mis coches") y está justo debajo de "Mis coches".
- **Asistencia** pasa al primer hueco de la tercera fila.

## 1.2.0 — 05/11/2026
- Nuevo: **Coches prestados**, en el menú de `/coche` (o `/coche prestados`). Salen los coches de otros jugadores de los que llevas la llave: modelo, matrícula, dueño y dónde está, y con un clic el GPS te lleva. Sin la llave no se puede conducir ni guardar en el inventario.

## 1.1.9 — 05/11/2026
- "Mis coches" (y el GPS) solo enseña los coches que están a tu nombre. Un coche prestado se sigue pudiendo conducir con la llave, pero ya no sale como tuyo.

## 1.1.8 — 05/11/2026
- Los coches ya no pueden cruzar portales del Nether o del End (se perdían sin su carrocería). Hay que bajarse y pasar andando.

## 1.1.7 — 05/11/2026
- Protección extra: golpear un coche sin mayúsculas no hace nada, tampoco en creativo. Un coche aparcado solo desaparece por siniestro, venta, desguace o guardándolo (mayúsculas + golpe).

## 1.1.6 — 05/11/2026
- Solo el dueño del coche (el que figura en el registro) puede cambiar la matrícula, hacer copias de la llave, contratar o cambiar el seguro y reclamar un siniestro. Quien solo tiene la llave puede conducirlo y pedir la grúa.

## 1.1.5 — 05/11/2026
- La moneda del server es el **Peso Matraco (Ⓜ)**: todos los precios salen con Ⓜ y con céntimos.

## 1.1.4 — 05/11/2026
- Arreglado: cualquiera que tuviera la llave podía vender o desguazar un coche ajeno. Ahora hace falta la llave **y** que el coche esté a tu nombre en el registro. Con la llave se puede seguir conduciendo (prestar el coche), pero no venderlo.

## 1.1.3 — 05/11/2026
- Arreglado: los demás jugadores veían al conductor quieto donde se subió y el coche moviéndose solo, como teledirigido. La vagoneta ya no se oculta a los demás (era eso lo que lo rompía); ahora es invisible y queda dentro de la carrocería.

## 1.1.2 — 05/11/2026
- Nombres de las aseguradoras: el seguro a terceros es ahora **Mutua Central** y el Todo Riesgo **Aseguradora Norte**. Las pólizas que ya hay siguen igual.

## 1.1.1 — 05/11/2026
- El seguro a terceros pasa a llamarse **Seguros Centrales**. Las pólizas que ya hay siguen igual.

## 1.1.0 — 05/11/2026
**Sonidos**
- Cada vehículo tiene su motor: suena más agudo cuanto más rápido vas y al ralentí más despacio.
- La furgoneta pita al dar marcha atrás.
- Aviso de reserva con un pitido propio (distinto del claxon) al entrar en reserva y cada 30 s mientras sigas en ella. Solo lo oye quien va dentro.
- Se pueden cambiar en el config.yml de Matracar: `sonidos.motor.activado: false` los quita; `sonidos.motor.<modelo>.sonido / volumen / cada / cada-parado / tono-min / tono-max` cambia el de cada modelo.

**Seguros**
- Atropellar monstruos o animales salvajes (sin dueño y fuera de una zona protegida) ya no cuenta para el seguro: ni indemnizaciones ni malus. Los animales domesticados, los de una protección, los aldeanos y los jugadores siguen contando.

**Arreglos**
- Si te desconectas montado en un coche, el coche se queda aparcado donde estaba (antes desaparecía).
- Quien conduce no puede usar la magia de Magic; los pasajeros sí.
- Tartana: techo un poco más alto, ya no asoma la cabeza.
- Turismo: el techo solar ahora es un hueco de verdad y la cabeza ya no atraviesa el techo.

## 1.0.1 — 05/11/2026
Retoques de carrocería (no cambia cómo se juega).
- Quad: el chasis ya no es negro, va del color del quad, y las ruedas se distinguen. Morro nuevo que tapa los pies. Asiento y manillar negros, portabultos grises. Los quads que ya existen cambian solos.
- Turismo: techo solar abierto (marco y cristal corrido hacia atrás) para que la cabeza asome de forma natural.
- Turismo, tartana y furgoneta: bajos grises entre las ruedas para que no se vean los pies.

## 1.0.0 — 05/11/2026
Primera versión para los jugadores. (Antes se llamaba "Coches"; los datos de plugins/Coches se copian solos la primera vez.)

**Menú**
- Escribir solo `/coche` abre la ventana de Matracar: concesionario, mis coches (seguro, grúa, copia de llave), coche en la mano (taller, vender a un jugador, desguace), gasolina, tickets y asistencia. La ayuda en texto está en `/coche ayuda`. Los comandos escritos siguen funcionando.

- GPS en el menú: dónde está cada uno de tus coches (o si lo llevas encima) y la gasolinera más cercana; una barra arriba con flecha y metros te lleva hasta allí.
- Carnet de conducir en el menú: km recorridos, horas al volante, choques, atropellos, bonus del seguro, gasolina gastada y coches comprados, vendidos y desguazados.

**Vehículos**
- Seis modelos con carrocería hecha con bloques de Minecraft (sin pack de texturas): Motocarro (1.500 $, el de empezar), Tartana (4.000 $), Quad (6.000 $), Turismo (10.000 $), Furgoneta (14.000 $) y Deportivo (25.000 $). Ninguno es el mejor en todo: el concesionario enseña su ficha con barras (velocidad, aceleración, frenada, giro, autonomía y maletero).
- Color y extras al azar al comprar (baca, rueda de repuesto, alerones, escalera…); algunos colores salen poco. Cada coche conserva su aspecto.
- Matrículas delante y detrás. La vagoneta de debajo es invisible para todos (solo la ve quien conduce, por dentro).
- Se conducen con WASD, claxon con espacio (cada modelo pita distinto, fuerte de cerca; `claxon.alcance` y `claxon.fuerza`); solo suben por escaleras (el quad también escalones de tierra, arena, grava, nieve o piedra).
- Se clica en cualquier parte de la carrocería: clic derecho para subirse; mayúsculas + clic derecho guarda (en la furgoneta abre la carga); mayúsculas + golpe guarda cualquiera.
- Freno de mano: aparcados no los mueve nadie.
- Maletero (solo con llave): motocarro 9, tartana 9, deportivo 9, turismo 18, furgoneta 27; el quad no tiene. Se guarda vacío; en siniestro total la carga cae al suelo; la grúa la trae a tu lado si va cargada.
- Llave obligatoria en el inventario para conducir. `/coche copiallave` (500 $) hace un duplicado.

**Gasolina**
- Precio real de la gasolina 95 (Ministerio, gasolineras a 12 km del punto configurado) x2 en $. Se consulta cada 6 h; si el Ministerio no responde, reintenta cada 30 min y mientras usa el último precio real (`/gasolina` dice si es de hoy o el último conocido).
- Gasolineras con nombres reales de la zona, cartel con precio, repostaje con espera y pago por litros.
- Tickets con diseño propio por gasolinera; 10 tickets válidos (≥ 5 $) = 1 repostaje gratis.

**Papeles**
- Matrículas por orden (0001 BBB…), registro y cambio de matrícula de pago.
- Seguros obligatorios (Terceros, Ampliado, Todo Riesgo), semanal o mensual, con bonus-malus. Atropellos con daño real e indemnizaciones. Vida del coche, averías, taller, grúa, asistencia y siniestro total.
- Compraventa entre jugadores (`/coche vender <jugador> <precio>` o desde el menú): el comprador acepta en una ventana, recibe una llave nueva y las anteriores dejan de valer; el seguro del vendedor se anula.
- Desguace (`/coche desguace` o desde el menú): el server paga hasta el 50 % del precio nuevo según el estado.

**Admin**
- `/gasolinera crear|renombrar|borrar|lista|ir|precio`
- `/coche dar <jugador> <modelo>`, `/coche llave <modelo> <matrícula>`, `/coche matriculas`, `/coche partes`
- `/coche reiniciar confirmar`: borra todo el parque de coches y las matrículas vuelven a 0001 BBB.
- Config: `modelos.giro-extra`, `modelos.matricula-al-reves`, `desguace.maximo`, `llaves.precio-copia`.


## Trenes (Matrains)

## 1.1.0 — 11/11/2026
- **Llaves de tren.** En el panel de la locomotora (mayúsculas + clic), el dueño tiene:
  - «🔑 Hacer copia de la llave» (500 Ⓜ, `llaves.precio-copia`): un gancho de cuerda con brillo con el código de la locomotora (T-123AB).
  - «Cambiar la cerradura» (gratis, mayúsculas + clic): todas las copias dejan de valer y quien conduzca con una vieja se baja.
- Con la llave encima, otro jugador puede conducir, repostar (carbón, surtidor o bidón), abrir la carga y manejar la ruta, el modo y la parada.
- Solo el dueño puede guardar el tren, enganchar o soltar vagones, cambiar el nombre, el billete y las llaves.
- La llave va con la locomotora: si la guardas y la vuelves a poner, las copias siguen valiendo.
- Los trenes prestados salen en «Mis vehículos» (/vehiculos) con 🔑 y el nombre del dueño.

## 1.0.2 — 11/11/2026
- Tus trenes salen también en «Mis vehículos» de MATRAMOTORS (/vehiculos).
- **/tren** a secas abre ahora el menú único de vehículos (como /coche y /barco). El menú de trenes sigue en el botón «🚆 Trenes» o con `/tren menu`, y tiene un botón para volver al de vehículos.

## 1.0.1 — 11/11/2026
- Las locomotoras diésel se repostan también con el **bidón de gasolina** de MATRAMOTORS: clic derecho a la locomotora con el bidón en la mano (la tuya o una que te dejen usar).

## 1.0.0 — 11/11/2026 (partes 4 y 5: estaciones, automáticos y concesionario)
**Para los jugadores: /tren** (también desde el botón «🚆 Trenes» de /coche)
- Concesionario ferroviario: locomotoras, vagones de mercancías, coches de pasajeros, campana de estación y subestación. La primera compra incluye el carné de tren (500 Ⓜ). Cada compra sale con un aspecto al azar (el de siempre, más a menudo).
- Las locomotoras y vagones son objetos: clic derecho en un raíl para ponerlos (mirando hacia donde irá el tren). Máximo 3 trenes por jugador.
- Mayúsculas + golpe en el tren, dentro de una estación tuya, lo guarda otra vez como objetos (con aspecto, combustible y carga).
- Mis trenes (dónde están, energía, ruta, luz pagada), Mis estaciones y Mis líneas eléctricas (encender y apagar la luz desde el menú).

**Estaciones**
- Campana de estación: al ponerla junto a la vía se abre un menú para elegir el tamaño (8x8 a 32x32, partículas en el borde) y confirmar. Nombre automático de Matracraft (se cambia en su menú) y código EST-0001. Máximo 5 por jugador.
- Dentro solo construye el dueño; los demás entran, suben y bajan y usan puertas. No puede pisar terreno protegido de otro ni solaparse con otra estación.
- Un cartel con [Salidas] en la primera línea, dentro de la estación, se convierte en panel: próximos trenes y cuándo llegan.
- Los vagones solo se sueltan dentro de una estación tuya.

**Trenes automáticos** (mayúsculas + clic en la locomotora → panel del tren)
- Ruta con las paradas en orden, modo ida y vuelta / bucle / solo ida, parada de 15-120 s, billete (lo pagan los demás al subir: 70 % al dueño del tren, 30 % al de la estación si es otro; se da un billete de papel).
- Buscan solos el camino en los desvíos, paran junto a la campana y siguen. Frenan si hay otro tren delante.
- Solo andan con el dueño conectado o con alguien subido; si no, esperan en la estación.
- Sin nadie cerca no se dibujan: un tramo que ya han hecho una vez a la vista lo hacen "por detrás" y aparecen en destino a su hora.
- Coche restaurante (viaje de 300 bloques o más) y Pullman (500 o más): comida o un licor de regalo al llegar, una vez por hora.

**Otros**: panel del tren con nombre, energía, carga, luz pagada; la diésel reposta desde el panel junto a un surtidor.

## 0.3.0 — 11/11/2026 (parte 3 de 5: combustible, luz y protección de las vías)
- El archivo pasa a llamarse Matrains.jar (sin número): a partir de ahora se actualiza sin borrar nada.
- Combustible: la de vapor quema carbón (clic derecho con carbón, carbón vegetal o bloques de carbón; 64 piezas, 1 cada 40 bloques). La diésel gasta gasóleo a precio real (el de MATRAMOTORS): mayúsculas + clic en la locomotora a menos de 10 bloques de un surtidor llena el depósito (300 L, 0,02 L por bloque).
- Luz: subestación (bloque de cobre especial) tocando un cable de netherrack; con una palanca pegada y encendida, toda la línea tiene luz (A-B-C, ramales, hasta 1000 bloques de cable). Las eléctricas, el AVE y el maglev solo tiran sobre vía con luz. La luz se paga a precio real del PVPC de Red Eléctrica (se lee cada hora) y la paga el dueño del tren según los bloques que recorre; sin saldo, el tren no tira.
- Protección propia de cada línea: el cable, la vía de encima y 1 bloque alrededor. Nadie ajeno rompe ni pone nada, ni mueve las palancas de los desvíos; el dueño solo decora (nada de cofres, hornos, tolvas…). Sin explosiones, pistones, líquidos, fuego, arena que cae, endermans ni árboles que crecen. No se pueden poner vagonetas ni barcos en la vía.
- La barra de abajo enseña siempre el combustible o la luz y lo que arrastra el tren.
- Pruebas (op): /matrains subestacion [n] da subestaciones; /matrains luz enseña el precio de la luz.

## 0.2.0 — 11/11/2026 (parte 2 de 5: locomotoras y vagones)
- Modelos de verdad: 5 locomotoras con 6 aspectos cada una (vapor, diésel, eléctrica, alta velocidad y maglev) y 13 vagones con 2-4 aspectos (abierto, furgón cerrado, tolva, plataforma de teleros, cisterna, frigorífico, garita de freno, coche de madera, cercanías de dos pisos, primera, restaurante, Pullman y mirador). Nombres inventados en los rótulos, nada de marcas reales.
- Tracción y peso: cada vagón pesa más cuanto más cargado va; si el tren pesa más de lo que arrastra la locomotora va más lento y acelera peor, y con mucho exceso no arranca. Límite de vagones por locomotora; alta velocidad y maglev solo llevan coches de pasajeros modernos.
- Enganche con cadena: clic derecho engancha lo que haya a menos de 3 bloques (siempre detrás de la locomotora); mayúsculas + clic suelta ese vagón y los de detrás. Con el tren parado y sin nadie subido.
- Carga: clic derecho en un vagón de mercancías abre su bodega (solo el dueño). Cada vagón admite lo suyo (la tolva un solo material a granel, la plataforma bloques de construcción, el frigorífico comida, la garita herramientas). La cisterna se llena y vacía con cubos (agua, lava o leche). La carga se guarda.
- Partículas: humo y vapor en la de vapor, humo negro al acelerar en la diésel, chispas en pantógrafo y ruedas en las eléctricas, brillo azul bajo el maglev.
- Coche de primera: regeneración mientras viajas. Mirador: visión nocturna en túneles. (Restaurante y Pullman dan sus premios con las estaciones de la parte 4.)
- Pruebas (op): /matrains probar <locomotora> [aspecto 1-6], /matrains vagon <tipo> [aspecto]. Los trenes de la 0.1 se recolocan solos con los nuevos largos.

## 0.1.0 — 11/11/2026 (parte 1 de 5: vías, física y conducción)
Versión de prueba para el administrador. Todavía sin concesionario, sin luz, sin estaciones ni rutas.
- Física propia sobre raíles normales (no usa la de las vagonetas): los propulsores y antorchas no hacen falta.
- Cada coche va sobre dos ejes que siguen la vía: el tren se dobla en las curvas y sube/baja cuestas.
- Frena solo antes de curvas cerradas (7 bloques/s) y del final de la vía; en tramos en diagonal (zigzag) va al 75 %.
- Conducción: W acelera, S frena y luego marcha atrás (30 %), A/D eligen rama en los desvíos (sin pulsar, manda la aguja vanilla), Espacio pita, Mayúsculas para bajarse. Sin maquinista, frena solo.
- Solo se dibuja con un jugador a menos de 96 bloques; se guarda en trenes.yml.
- Comandos de prueba (op): /matrains probar <vapor|diesel|electrica|ave|maglev> [vagones 0-8] [abierto|basico], /matrains quitar, /matrains lista.
- Modelos provisionales: los de verdad llegan en la parte 2.
