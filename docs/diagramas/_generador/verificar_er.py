"""
Comprueba que 02-er.drawio coincide con database/schema.sql, columna a columna:
nombre, tipo, nulidad, marcas PK/FK/UK, valores de los ENUM y claves ajenas
(con su ON DELETE). También comprueba las notas: el nombre de cada clave única
compuesta (leyenda), los valores por defecto (DEFAULT) y que cada restricción
CHECK del esquema esté recogida en la nota. Devuelve código de salida 1 si hay
alguna diferencia.

Uso:  python verificar_er.py
"""
from __future__ import annotations

import html
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
SCHEMA = RAIZ / "database" / "schema.sql"
DRAWIO = Path(__file__).resolve().parent.parent / "02-er.drawio"


# CHECK del esquema -> frase que debe aparecer en la nota «Restricciones CHECK» del diagrama
CHECKS_EN_NOTA = {
    "ck_usuarios_precio": "usuarios: precio_corte > 0",
    "ck_horarios_dia": "dia_semana de 1 a 7",
    "ck_horarios_tramo": "hora_inicio < hora_fin",
    "ck_excepciones_coherencia": "«cerrado» sin horas ni cupo",
    "ck_citas_precio": "citas: precio y propina ≥ 0",
    "ck_citas_propina": "citas: precio y propina ≥ 0",
}


# ----------------------------------------------------------------------------- schema.sql
def partes_de_nivel_superior(texto: str) -> list[str]:
    """Divide por comas ignorando las que van entre paréntesis o entre comillas."""
    partes, actual, nivel, comilla = [], [], 0, None
    for ch in texto:
        if comilla:
            actual.append(ch)
            if ch == comilla:
                comilla = None
            continue
        if ch in "'\"":
            comilla = ch
        elif ch == "(":
            nivel += 1
        elif ch == ")":
            nivel -= 1
        elif ch == "," and nivel == 0:
            partes.append("".join(actual).strip())
            actual = []
            continue
        actual.append(ch)
    if "".join(actual).strip():
        partes.append("".join(actual).strip())
    return partes


def quitar_comentarios_de_linea(sql: str) -> str:
    """Quita los '-- ...' que no estén dentro de una cadena entre comillas simples."""
    salida = []
    for linea in sql.splitlines():
        comilla, corte = False, None
        for i, ch in enumerate(linea):
            if ch == "'":
                comilla = not comilla
            elif not comilla and linea.startswith("--", i):
                corte = i
                break
        salida.append(linea if corte is None else linea[:corte])
    return "\n".join(salida)


def leer_schema() -> dict:
    sql = quitar_comentarios_de_linea(SCHEMA.read_text(encoding="utf-8"))
    tablas = {}
    for m in re.finditer(r"CREATE TABLE (\w+)\s*\((.*?)\)\s*ENGINE=InnoDB;", sql, re.S):
        nombre, cuerpo = m.group(1), m.group(2)
        columnas, pk, uks, fks, enums = [], [], [], [], {}
        nombres_uk, checks, defaults = {}, [], {}
        for parte in partes_de_nivel_superior(cuerpo):
            parte = " ".join(parte.split())
            if parte.startswith("PRIMARY KEY"):
                pk = re.findall(r"\((.*?)\)", parte)[0].replace(" ", "").split(",")
            elif parte.startswith("UNIQUE KEY"):
                cols_uk = re.findall(r"\((.*?)\)", parte)[0].replace(" ", "").split(",")
                uks.append(cols_uk)
                nombres_uk[tuple(cols_uk)] = parte.split()[2]
            elif parte.startswith("CONSTRAINT") and "FOREIGN KEY" in parte:
                f = re.search(r"FOREIGN KEY \((\w+)\) REFERENCES (\w+) \((\w+)\)( ON DELETE ([A-Z ]+))?", parte)
                fks.append((f.group(1), f.group(2), f.group(3), (f.group(5) or "").strip()))
            elif parte.startswith(("KEY", "CONSTRAINT")):
                if " CHECK " in parte:
                    checks.append(parte.split()[1])
            else:
                col = parte.split()[0]
                resto = parte[len(col):].strip()
                t = re.match(r"(ENUM\(.*?\)|[A-Z]+(?: UNSIGNED)?(?:\(\d+(?:,\d+)?\))?)", resto)
                tipo = t.group(1)
                generada = "GENERATED ALWAYS" in resto
                # NOT NULL explícito -> no admite nulos; una columna generada sí puede dar NULL
                nulo = ("NOT NULL" not in resto) or generada
                if tipo.startswith("ENUM"):
                    enums[col] = re.findall(r"'(\w+)'", tipo)
                    tipo = "ENUM"
                columnas.append(dict(nombre=col, tipo=tipo, nulo=nulo, generada=generada))
                d = re.search(r"DEFAULT\s+('[^']*'|[\w.]+)", resto)
                if d:
                    defaults[col] = d.group(1)
        tablas[nombre] = dict(columnas=columnas, pk=pk, uks=uks, fks=fks, enums=enums,
                              nombres_uk=nombres_uk, checks=checks, defaults=defaults)
    return tablas


# ----------------------------------------------------------------------------- .drawio
def limpiar_html(valor: str) -> str:
    sin_etiquetas = re.sub(r"<[^>]+>", "", valor or "")
    return re.sub(r"\s+", " ", html.unescape(sin_etiquetas).replace(" ", " ")).strip()


def leer_drawio() -> tuple[dict, list, list[str]]:
    raiz = ET.parse(DRAWIO).getroot()
    celdas = {c.get("id"): c for c in raiz.iter("mxCell")}
    hijos: dict[str, list] = {}
    for c in celdas.values():
        hijos.setdefault(c.get("parent"), []).append(c)

    def x_de(c):
        return float(c.find("mxGeometry").get("x", "0"))

    def y_de(c):
        return float(c.find("mxGeometry").get("y", "0"))

    tablas = {}
    for c in celdas.values():
        if (c.get("style") or "").startswith("shape=table;"):
            filas = []
            for fila in sorted(hijos.get(c.get("id"), []), key=y_de):
                cs = sorted(hijos.get(fila.get("id"), []), key=x_de)
                filas.append([limpiar_html(k.get("value")) for k in cs])
            tablas[c.get("value")] = dict(id=c.get("id"), filas=filas)

    def tabla_de(id_celda):
        while id_celda:
            for n, t in tablas.items():
                if t["id"] == id_celda:
                    return n
            id_celda = celdas[id_celda].get("parent")
        return None

    aristas = []
    for c in celdas.values():
        if c.get("edge") == "1" and c.get("source") and c.get("target"):
            aristas.append((tabla_de(c.get("source")), tabla_de(c.get("target")),
                            c.get("value") or "", c.get("style") or ""))
    textos = [limpiar_html(c.get("value")) for c in celdas.values() if c.get("vertex") == "1"]
    return tablas, aristas, textos


# ----------------------------------------------------------------------------- comparación
def main() -> int:
    sch = leer_schema()
    dio, aristas, textos = leer_drawio()
    errores = []

    if set(sch) != set(dio):
        errores.append(f"Tablas distintas: schema={sorted(sch)} drawio={sorted(dio)}")

    for nombre, t in sch.items():
        if nombre not in dio:
            continue
        marcas = {}
        for c in t["pk"]:
            marcas[c] = "PK"
        for uk in t["uks"]:
            for c in uk:
                marcas[c] = "UK" if len(uk) == 1 else "UK1"
        for c, *_ in t["fks"]:
            marcas[c] = "FK"
        esperado = []
        for col in t["columnas"]:
            tipo = col["tipo"] + (" NULL" if col["nulo"] else "")
            if col["generada"]:
                tipo += " (generada)"
            esperado.append([marcas.get(col["nombre"], ""), col["nombre"], tipo])
        real = dio[nombre]["filas"]
        if real != esperado:
            errores.append(f"{nombre}: filas distintas")
            for i in range(max(len(real), len(esperado))):
                a = esperado[i] if i < len(esperado) else None
                b = real[i] if i < len(real) else None
                if a != b:
                    errores.append(f"   fila {i + 1}: schema={a} drawio={b}")

        for col, valores in t["enums"].items():
            frase = f"{nombre}.{col}: " + ", ".join(valores)
            if not any(frase in x for x in textos):
                errores.append(f"ENUM {nombre}.{col}: falta o difiere en la nota ({frase})")

    # claves únicas compuestas: la leyenda debe nombrarlas como en el esquema
    leyenda = next((x for x in textos if "sin ella, NOT NULL" in x), "")
    for nombre, t in sch.items():
        for cols, nombre_uk in t["nombres_uk"].items():
            if len(cols) > 1 and f"UK1 = {nombre_uk}" not in leyenda:
                errores.append(f"Falta «UK1 = {nombre_uk}» en la leyenda ({nombre}.{','.join(cols)})")

    # restricciones CHECK: cada una del esquema debe estar en la lista de abajo y su
    # texto en la nota; si el esquema añade o quita una, hay que revisar la nota
    for nombre, t in sch.items():
        for ck in t["checks"]:
            if ck not in CHECKS_EN_NOTA:
                errores.append(f"CHECK {ck} ({nombre}) no está recogido en la nota del diagrama")
    for ck, frase in CHECKS_EN_NOTA.items():
        if not any(ck in t["checks"] for t in sch.values()):
            errores.append(f"La nota recoge {ck}, que ya no existe en el esquema")
        elif not any(frase in x for x in textos):
            errores.append(f"CHECK {ck}: falta en la nota la frase «{frase}»")

    # valores por defecto: «columna = valor» en la nota (decimales con coma)
    for nombre, t in sch.items():
        for col, valor in t["defaults"].items():
            if valor.upper() == "CURRENT_TIMESTAMP":
                continue            # creado_en / creada_en: no se anotan
            if re.fullmatch(r"\d+\.\d+", valor):
                valor = valor.replace(".", ",")
            esperado = f"{col} = {valor}"
            if not any(esperado in x for x in textos):
                errores.append(f"DEFAULT {nombre}.{col}: falta «{esperado}» en la nota")

    # claves ajenas: una arista por cada FK, con su columna y su ON DELETE en la etiqueta
    for nombre, t in sch.items():
        for col, tabla_pad, _col_pad, on_delete in t["fks"]:
            ok = any(o == tabla_pad and d == nombre and col in v and on_delete in v
                     for o, d, v, _ in aristas)
            if not ok:
                errores.append(f"FK {nombre}.{col} -> {tabla_pad} ({on_delete}) sin arista correcta")
    n_fk = sum(len(t["fks"]) for t in sch.values())
    n_rel = sum(1 for o, d, v, e in aristas if o in sch and d in sch)
    if n_rel != n_fk:
        errores.append(f"Aristas entre tablas={n_rel} pero el esquema tiene {n_fk} claves ajenas")

    # cardinalidades en el lado de usuarios (el lado hijo es siempre 0..N)
    for o, d, v, e in aristas:
        if o not in sch or d not in sch:
            continue        # p. ej. el conector discontinuo de la nota
        if "usada_por" in v and "startArrow=ERzeroToOne" not in e:
            errores.append("usada_por debe ser 0..1 en el lado de usuarios")
        if ("creada_por" in v or "cliente_id" in v) and "startArrow=ERmandOne" not in e:
            errores.append(f"{v}: debe ser 1 en el lado de usuarios")
        if "endArrow=ERzeroToMany" not in e:
            errores.append(f"{v}: el lado hijo debe ser 0..N")

    for n in ("horarios_semanales", "excepciones_dia"):
        if any(n in (o, d) for o, d, *_ in aristas):
            errores.append(f"{n} no debe tener relaciones")

    n_cols = sum(len(t["columnas"]) for t in sch.values())
    if errores:
        print("DIFERENCIAS:")
        print("\n".join(errores))
        return 1
    print(f"OK: {len(sch)} tablas, {n_cols} columnas, {n_fk} claves ajenas; "
          "el diagrama coincide con schema.sql")
    return 0


if __name__ == "__main__":
    sys.exit(main())
