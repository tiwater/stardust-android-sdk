package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.FaceDetail
import cn.ticos.stardust.sample.model.VisionResultItem
import cn.ticos.stardust.sample.ui.theme.AppColors
@Composable
fun VisionResultPanel(
    results: List<VisionResultItem>,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) return

    LazyColumn(
        modifier = modifier.heightIn(max = 200.dp),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.id }) { result ->
            VisionResultCard(result)
        }
    }
}

@Composable
private fun VisionResultCard(result: VisionResultItem) {
    Surface(
        color = AppColors.Gray50,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            result.faceInfo?.let { face ->
                val maxConf = face.faces.maxOfOrNull(FaceDetail::confidence)
                val confPct = maxConf?.let { (it * 100).toInt() }
                Text(
                    text = if (confPct != null) {
                        stringResource(R.string.vision_face_line, face.faceCount, confPct)
                    } else {
                        stringResource(R.string.vision_face_line_no_conf, face.faceCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            result.objectInfo?.let { obj ->
                obj.objects.forEach { detail ->
                    Text(
                        text = stringResource(
                            R.string.vision_object_line,
                            detail.label,
                            (detail.confidence * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            result.handInfo?.let { hand ->
                hand.hands.forEach { detail ->
                    Text(
                        text = stringResource(
                            R.string.vision_hand_line,
                            detail.gesture,
                            (detail.confidence * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            result.imageInfo?.let { img ->
                img.labels.forEach { label ->
                    Text(
                        text = stringResource(R.string.vision_image_label_line, label),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                img.description?.let { desc ->
                    Text(
                        text = stringResource(R.string.vision_image_desc_line, desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
