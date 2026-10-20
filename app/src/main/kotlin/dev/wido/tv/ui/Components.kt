package dev.wido.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Bouton pilule activable à la télécommande. `primary` : verre plus clair. `compact` : version discrète. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    compact: Boolean = false,
    idleText: Color = Palette.TextPrimary,
) {
    FocusSurface(onClick, modifier, idle = if (primary) Palette.GlassStrong else Color.White.copy(alpha = 0.10f)) { focused ->
        Text(
            label,
            color = onSurface(focused, idleText),
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = if (primary || focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (compact) 18.dp else 22.dp, vertical = if (compact) 8.dp else 11.dp),
        )
    }
}

/** Pastille activable : un réglage à bascule ou un choix (`selected` = verre clair), ou un domaine à ajouter / retirer. */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    idleText: Color = if (selected) Palette.TextPrimary else Color.White.copy(alpha = 0.8f),
    idle: Color = if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
) {
    FocusSurface(onClick, modifier, idle = idle, focusScale = 1.08f) { focused ->
        Text(
            label,
            color = onSurface(focused, idleText),
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier.padding(top = 6.dp),
    )
}

/** Panneau de verre : translucide, bord clair d'un pixel, ombre douce. */
fun Modifier.glassPanel(color: Color = Palette.Panel, shape: Shape = PanelShape, elevation: Dp = 0.dp): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(color)
    .border(1.dp, Palette.GlassBorder, shape)

/**
 * Champ de saisie en verre, pensé pour la télécommande : le focus ne fait que le sélectionner (il
 * s'éclaire), **OK** entre dans la saisie et ouvre le clavier. Valider au clavier, ou quitter le champ
 * avec les flèches, termine la saisie. `onPhone` ajoute à droite un bouton « saisir au téléphone ».
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester? = null,
    onPhone: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    // Après une saisie, le focus revient sur le champ affiché (et non au début de la fenêtre).
    var refocus by remember { mutableStateOf(false) }
    val display = focusRequester ?: remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun finish() {
        keyboard?.hide()
        editing = false
        refocus = true
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            if (editing) {
                val input = remember { FocusRequester() }
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    runCatching { input.requestFocus() }
                    keyboard?.show()
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Jakarta, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = KeyboardActions(onDone = { finish() }, onNext = { finish() }, onGo = { finish() }, onSearch = { finish() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(input)
                        .onFocusChanged {
                            if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
                        },
                    decorationBox = { inner -> FieldBox(label, focused = true) { inner() } },
                )
            } else {
                LaunchedEffect(refocus) {
                    if (refocus) {
                        refocus = false
                        runCatching { display.requestFocus() }
                    }
                }
                FocusSurface(
                    onClick = { editing = true },
                    modifier = Modifier.fillMaxWidth().focusRequester(display),
                    shape = RowShape,
                    idle = Color.White.copy(alpha = 0.10f),
                    focusScale = 1.01f,
                    contentAlignment = Alignment.CenterStart,
                ) { focused ->
                    FieldBox(label, focused) {
                        Text(
                            value.ifEmpty { " " },
                            color = onSurface(focused),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (onPhone != null) {
            FocusSurface(onPhone, Modifier.size(58.dp), shape = RowShape, idle = Color.White.copy(alpha = 0.10f)) { focused ->
                PhoneIcon(onSurface(focused))
            }
        }
    }
}

/** Libellé au-dessus de la valeur, sur fond blanc (sélectionné ou en saisie) ou verre. */
@Composable
private fun FieldBox(label: String, focused: Boolean, value: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (focused) Palette.InkSecondary else Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        value()
    }
}

/** Petit téléphone dessiné : contour arrondi et bouton. */
@Composable
fun PhoneIcon(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width * 0.52f
        val h = this.size.height * 0.88f
        val left = (this.size.width - w) / 2
        val top = (this.size.height - h) / 2
        val stroke = this.size.width * 0.09f
        drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(w * 0.22f), style = Stroke(stroke))
        drawCircle(color, radius = stroke * 0.7f, center = Offset(this.size.width / 2, top + h - stroke * 2.2f))
    }
}

/**
 * Cadre commun des fenêtres : un panneau de verre, titre en haut, contenu défilant au milieu, boutons
 * toujours visibles en bas. Ainsi rien ne se superpose, quelle que soit la taille du contenu.
 */
@Composable
fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 720.dp,
    buttons: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(width)
                .heightIn(max = 480.dp)
                .glassPanel(Palette.PanelSolid, elevation = 30.dp)
                .padding(horizontal = 30.dp, vertical = 26.dp),
        ) {
            DialogBody(title, buttons, content = content)
        }
    }
}

/** Titre, contenu défilant et boutons : le corps commun des fenêtres. `fill` : les boutons restent collés en bas. */
@Composable
fun ColumnScope.DialogBody(
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        color = Palette.TextPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.weight(1f, fill = fill).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
}
