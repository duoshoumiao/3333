package com.pcrjjc.app.ui.components  
  
import androidx.compose.foundation.Image  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.RowScope  
import androidx.compose.material3.*  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.produceState  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.graphics.ImageBitmap  
import androidx.compose.ui.graphics.asImageBitmap  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.res.painterResource  
import com.pcrjjc.app.R  
import com.pcrjjc.app.util.TopBarBgStorage
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun ImageTopAppBar(  
    title: @Composable () -> Unit,  
    modifier: Modifier = Modifier,  
    navigationIcon: @Composable () -> Unit = {},  
    actions: @Composable RowScope.() -> Unit = {}  
) {  
    val context = LocalContext.current  
    val version by TopBarBgStorage.version.collectAsState()  
    val customBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(  
        initialValue = null,  
        key1 = version  
    ) {  
        value = TopBarBgStorage.load(context)?.asImageBitmap()  
    }  
  
    Box(modifier = modifier) {  
        if (customBitmap != null) {  
            Image(  
                bitmap = customBitmap!!,  
                contentDescription = null,  
                modifier = Modifier.matchParentSize(),  
                contentScale = ContentScale.Crop  
            )  
        } else {  
            Image(  
                painter = painterResource(R.drawable.topbar_bg),  
                contentDescription = null,  
                modifier = Modifier.matchParentSize(),  
                contentScale = ContentScale.Crop  
            )  
        }
        TopAppBar(  
            title = title,  
            navigationIcon = navigationIcon,  
            actions = actions,  
            colors = TopAppBarDefaults.topAppBarColors(  
                containerColor = Color.Transparent,  
                titleContentColor = Color.Black,  
				navigationIconContentColor = Color.Unspecified, 
                actionIconContentColor = Color.Unspecified
            )  
        )  
    }  
}