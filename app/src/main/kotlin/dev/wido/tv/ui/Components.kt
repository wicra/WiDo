package dev.wido.tv.ui

import androidx.compose.animation.animateColorAsState
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
 * Champ de saisie en verre : le libellé au-dessus de la valeur. Avec le focus, il devient blanc,
 * le texte passe en encre et le curseur en bleu.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.10f), label = "fieldFill")
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = Jakarta,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Palette.Ink else Palette.TextPrimary,
        ),
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .liftEffect(focused, RowShape, focusScale = 1.01f),
        decorationBox = { inner ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RowShape)
                    .background(background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    label,
                    color = if (focused) Palette.InkSecondary else Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                inner()
            }
        },
    )
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
