# Posibles bugs del Matracar 1.2.2 original (detectados al reconstruir)
- Coches.onEnable: Extra.iniciar(this) se ejecuta antes de saveDefaultConfig/claves/registro (parche a mano sobre el .class).
- Coches.comprar: se ignora el resultado de seguros.contratar(...) tras cobrar coche + seguro (si falla, pagó sin póliza).
- /gasolinera tab: no pasa args[0] a minúsculas.
- /coche tab: faltan menu, ayuda, aceptar, rechazar, registro, tarjeta.
