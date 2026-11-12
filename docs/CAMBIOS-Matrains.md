# Matrains — registro de cambios

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
