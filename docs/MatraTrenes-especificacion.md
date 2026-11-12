# MatraTrenes — especificación cerrada (11/11/2026, 20:13)

Base: documento original (`matratrenes-diseno.txt`) + decisiones del admin. Referencias en `trenes-ref/`.
Plugin aparte de MATRAMOTORS, pero reutiliza su base invisible (soporte de armadura escalado), sus piezas giradas (Modelos.Pieza), seguros, gasolina real y economía (Vault).

## Núcleo
- Física propia sobre raíles vanilla (no la de las vagonetas). El tren sigue el camino de raíles; cada vagón apoya dos ejes que siguen la trayectoria de la locomotora; hueco de 0,5 bloques entre vagones; vagones de 3-4 bloques.
- Manual: W/S adelante/atrás, salto = pitar, mayúsculas = bajarse, freno de mano. En una Y: A/D eligen rama; sin pulsar, manda la palanca vanilla del dueño.
- Automáticos: eligen la rama hacia su siguiente parada. Bloqueo por cantones: no entran en un tramo ocupado.
- Fuera de la vista: el tren no existe como entidad; posición calculada por tiempo; aparece y se mueve de verdad cuando un jugador está a distancia de visión. Se puede subir parado o muy despacio; a un automático se sube pagando hasta la siguiente parada.
- Mercancías programadas: viaja a destino y se queda allí hasta que el dueño descargue o se suba para volver.
- Desgaste por bloques recorridos → más lento → revisión de pago. Sin vida/explosiones. Choque manual: ambos paran. Atropello: empuja + daño, paga el seguro del dueño (Seguros de MATRAMOTORS).
- Enganche: cadena + clic derecho = enganchar a la cola más cercana (≤3 bloques); mayúsculas + clic = soltar. Rutas pueden soltar/recoger vagones en estaciones.

## Energía y protección
- Cable: netherrack continuo motor→receptor (máx. 1000), comprobado al accionar palanca o al tocar el tendido; cacheado.
- Luz a precio real (PVPC), la paga el dueño del motor; factura consultable.
- Protección propia (no GriefPrevention) de vía + netherrack + 3x3 alrededor: nadie ajeno rompe/pone; inmune a explosiones, pistones, fuego, lava/agua, caída de arena/grava, endermans, crecimiento de árboles; no se pueden poner vagonetas/barcos/coches (el plugin borra vagonetas ajenas). El dueño solo decora y pone redstone: nada de cofres, hornos, tolvas, aldeanos ni contenedores dentro. La línea se desmonta entera por menú. Netherrack no arde.
- Puntos de socorro cada X bloques (configurable).

## Locomotoras
| Tier | Tipo | Vel. máx. (b/s) | Tracción | Vagones máx. | Combustible | Arrastra | Precio |
|---|---|---|---|---|---|---|---|
| 1 | Vapor | 12 | 6 | 4 | Carbón en ténder (~1/40 bloques) | todo | 6.000 |
| 2 | Diésel | 16 | 10 | 6 | Gasóleo real (gasolineras) | todo | 15.000 |
| 3 | Eléctrica | 20 | 14 | 8 | Luz 1,0/bloque | todo | 30.000 |
| 4 | Alta velocidad | 26 | 5 | 4 | Luz 0,8/bloque | solo pasajeros | 60.000 |
| 5 | Maglev | 32 | 4 | 3 | Luz 0,6/bloque | solo pasajeros | 120.000 |
Velocidad efectiva = máx × min(1, tracción/peso)^0,5; aceleración y frenada empeoran igual.
Partículas: vapor humo de chimenea + vapor en cilindros; diésel humo negro al acelerar; eléctrica chispas en pantógrafo y ruedas; AV chispas pequeñas en pantógrafo; maglev destellos azulados bajo el tren.

### Skins de locomotoras (6 por tier, color dominante + cristal tintado, puertas, vinilos, morro, faros distintos)
1 Vapor (ref. GNR 85): Azul caldera/frontal negro/bandas rojas · Verde británica con filetes dorados · Negra minera con ruedas rojas · Granate con cobre · Negra y dorada "de gala" · Gris oxidada con parches.
2 Diésel (ref. Union Pacific / Rio Grande): Amarilla con franja plata y negra · Amarilla con morro gris · Roja y plata "warbonnet" · Azul y amarilla · Verde y crema · Negra con franjas naranjas de seguridad.
3 Eléctrica (ref. rusa roja-gris / FLIRT): Roja con frontal naranja y faldón gris · Blanca con agua azul (gotas) · Gris con franja verde · Azul oscura con frontal amarillo · Blanca y roja de cercanías · Naranja y negra de mercancías.
4 Alta velocidad (ref. AVE S-103 / Avril): Blanca con franja morada · Blanca con franja roja y cristal ahumado · Plata con franja azul · Blanca con morro negro y cristal azulado · Blanca con franja dorada "Matraca" · Gris grafito con franja turquesa.
5 Maglev (ref. Shinkansen E5 / SCMaglev L0): Verde y blanca con línea rosa · Blanca con franjas azules · Blanca con morro azul y cristal ámbar · Negra con línea cian · Plata con línea roja · Blanca con franja violeta.

## Vagones de mercancías (peso vacío / lleno)
| Vagón | Capacidad | Acepta | Peso | Precio |
|---|---|---|---|---|
| Abierto (borde alto) | 27 | todo menos líquidos | 1 / 2 | 1.500 |
| Furgón cerrado | 54 | todo menos líquidos | 1,5 / 3 | 3.500 |
| Tolva | 54 | un solo material a granel (el primero que metas) | 1,5 / 2,5 | 5.000 |
| Plataforma de teleros | 54 | troncos, tablones, bloques de construcción | 1 / 2,5 | 4.000 |
| Cisterna | 128 cubos | agua, lava o leche (una), con cubos | 1,5 / 3 | 6.000 |
| Frigorífico | 27 | solo comida (+10 % al vender si hay tienda que compre) | 1,5 / 2 | 5.000 |
| Garita de freno | 2 | herramientas; frena antes y lleva punto de socorro | 0,5 | 2.500 |
Skins (3-4 cada uno): furgón marrón/rojo óxido/gris/azul de reparto; abierto marrón/negro/verde; tolva roja "Cervezas Matraca"/gris "Cementos del Faro"/amarilla "Cereales Villarroble"; cisterna gris franja azul "Petróleos del Faro"/blanca franja roja/negra; frigorífico blanco/crema/azul claro; plataforma gris/negra/roja. Nada de marcas reales.

## Vagones de pasajeros (elección de Claude)
| Vagón | Asientos | Bonus | Peso | Precio |
|---|---|---|---|---|
| Básico (madera, años 20) | 2 | — | 1 | 2.000 |
| Cercanías (dos pisos) | 6 | — | 1,5 | 6.000 |
| Premium (primera clase) | 4 | Regeneración I y saturación leve mientras viajas | 1,5 | 12.000 |
| Restaurante | 2 | Un plato gratis por viaje (≥300 bloques, 1 h de espera) | 1,5 | 10.000 |
| Regalo (Pullman de lujo) | 2 | Al llegar, 1 objeto aleatorio (bebida con efecto o de valor de venta); trayecto mínimo 500 bloques reales entre estaciones distintas, 1 h por jugador | 2 | 20.000 |
| Mirador panorámico (para AV/maglev) | 4 | Techo de cristal, visión nocturna en túneles | 1,5 | 15.000 |
Skins: básico madera barnizada/verde y crema/granate; cercanías blanco-verde/blanco-rojo/azul-amarillo; premium azul noche con filete dorado/granate-crema/plata-morado (a juego con AV); restaurante granate y dorado/azul y crema; regalo azul Pullman con dorado/negro y oro; mirador blanco-cian/plata-azul.
Los vagones de pasajeros tienen el estilo de su época: los de AV/maglev solo enganchan con premium, restaurante, regalo y mirador "modernos".

## Estaciones y rutas (del documento original)
Campana central, área 8x8 a 32x32, ID EST-0001; tarifas 70 % dueño del tren / 30 % estación; rutas ida y vuelta / solo ida / bucle; parada 15/30/60/120 s; cobro al subir o al bajar; sin saldo no se sube. Menú /tren estilo MatraPanel (cristal gris, prefijo [MatraTrenes]).

## Pendiente al empezar
Formato de datos (YAML como el resto de plugins), materiales de bloques Motor/Receptor, herramienta de área de estación, recetas o compra en concesionario (mismo menú que MATRAMOTORS), valores finales en config.

## Decisiones 20:40
- Compra: en el concesionario, como los coches (pestaña/menú "Ferrocarril" en el mismo estilo). Vías y netherrack los pone el jugador.
- Un solo bloque eléctrico: "Subestación" (bloque de cobre encerado con PDC, se compra en el concesionario). Todo lo unido por netherrack forma una RED (A-B-C, ramales). Una subestación con su palanca ON alimenta toda la red; las demás hacen de nodo/punto de enganche de estaciones. La luz la paga el dueño de la(s) subestación(es) encendida(s) (si hay varias de distintos dueños, a partes iguales). Recalcular la red solo al tocar netherrack/subestación o accionar palanca. Máx. 1000 bloques de netherrack por red (configurable).
- (20:48) Colocación: la subestación NO tiene que ir bajo la vía. Cuenta como parte de la red si toca cualquier bloque de netherrack de la línea por alguna cara (al lado de la vía, en el andén, o en la propia línea). La palanca va pegada a la subestación (encima o en un lateral); también se enciende/apaga desde /tren → Mis líneas eléctricas. Palanca protegida: solo el dueño.

## Decisiones 20:55
- Nombre: **Matrains**. Plugin aparte que se apoya en MATRAMOTORS (seguros, gasóleo, concesionario, base invisible).
- Mundos: world y world_nether.
- Raíles normales; los propulsores/antorchas no hacen falta (solo decoración).
- Límites por jugador (config): 3 trenes, 5 estaciones, 2 redes eléctricas, 4 subestaciones por red. Una red puede tener tantas estaciones como toquen su cable.
- Estación: se compra "Campana de estación" en el menú (~5.000 Ⓜ); se coloca junto a la vía y su menú "Crear estación" marca un área 16x16 (ajustable 8-32 con botones, sin herramienta) y altura desde 4 bajo la vía a 12 sobre ella; partículas muestran el borde antes de confirmar. Protegida para el dueño (otros pueden entrar, subir/bajar, usar asientos/puertas). No puede pisar terreno de GriefPrevention ajeno sin permiso de construir ni solaparse con otra estación.
- Menú UNIFICADO de MATRAMOTORS (rehacer al hacer Matrains): un solo comando (/vehiculos; /coche, /barco, /tren como alias que abren el mismo menú). Menú principal sin iconos redundantes: Concesionario (pestañas Coches · Embarcaciones · Ferrocarril), Mis vehículos, Seguros, Carné, Gasolineras, Taxímetro, Trenes (estaciones, redes, rutas, facturas). Todo usable por default sin comandos.
- (20:57) Mis vehículos: propios + prestados (llaves ajenas), incluidos trenes.

## Decisiones 21:04
- Vías en 3 modos por el dueño: privada (dueño + llaves), abierta (gratis), de pago (peaje por bloque al dueño). Luz que gasta un tren ajeno la paga el dueño del tren. Infraestructura y trenes pueden tener dueños distintos (futuro: subcontratas/trabajadores, reparto de ganancias).
- Conducción: dueño + llaves. Carné de tren automático al comprar con tasa (futura autoescuela).
- Pasos a nivel: coche empujado y dañado, paga seguro del dueño del tren; no aparcar sobre la vía. Mobs: apartados, sin morir.
- Automáticos: funcionan si el dueño está conectado, o si hay otro jugador subido usándolo; si no, al llegar a la siguiente estación se quedan esperando. Nada de dejar trenes de mercancías trabajando con el dueño desconectado.
- Guardar tren: mayúsculas+golpe solo dentro de estación propia.
- Panel de salidas SIN hologramas: carteles normales; el dueño escribe [Salidas] en la primera línea de un cartel dentro de la estación y el plugin lo actualiza cada pocos segundos (próximo tren, destino, tarifa).
- Billete de papel como recibo. Sonidos: silbato al salir, campana al llegar. Reventa al 50 %.

## Decisiones 21:10
- Vagones sueltos solo dentro de estación. Carga: abren dueño + llaves. Cuestas: más lento según carga (vapor cargada puede no subir). Sin límite de altura; soportes dentro de la zona protegida.
- Curvas: el tren reduce solo (calcular radio con la trayectoria para que no se vea brusco), sin descarrilar.
- Combustible: precio ÚNICO de gasolina para todo (quitar suplemento náutico de 0,15 €/L de MATRAMOTORS; el taxi boat mantiene su tarifa). Nuevo objeto "Bidón de gasolina" (10 L) comprado en cualquier gasolinera; sirve para coches, barcos y trenes diésel; vapor con carbón.
- Economía real (11/11): dinero de jugadores 200–11.000 Ⓜ (admin 574k). Coches 1.500–25.000, barcos 1.200–45.000. Precios de trenes reajustados: Vapor 4.000 · Diésel 9.000 · Eléctrica 18.000 · AV 35.000 · Maglev 60.000. Vagones 800–8.000 (Regalo 10.000). Campana de estación 2.500, subestación 1.500, carné de tren 500.
- Trenes con nombre puesto por el dueño. Estaciones y subestaciones con nombre automático del lore de Matracraft (lista estilo nombres náuticos de gasolineras), renombrable.
- TODO: revisar el resto de plugins (Jobs, ChestShop, MATRABANK, ClimaReal, GP...) antes de programar para no pisar comandos/eventos.
