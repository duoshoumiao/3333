package com.pcrjjc.app.ui.components  
  
import androidx.compose.foundation.Image  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.RowScope  
import androidx.compose.material3.*  
import androidx.compose.runtime.Composable  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.res.painterResource  
import com.pcrjjc.app.R  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun ImageTopAppBar(  
    title: @Composable () -> Unit,  
    modifier: Modifier = Modifier,  
    navigationIcon: @Composable () -> Unit = {},  
    actions: @Composable RowScope.() -> Unit = {}  
) {  
    Box(modifier = modifier) {  
        Image(  
            painter = painterResource(R.drawable.topbar_bg),  
            contentDescription = null,  
            modifier = Modifier.matchParentSize(),  
            contentScale = ContentScale.Crop  
        )  
        TopAppBar(  
            title = title,  
            navigationIcon = navigationIcon,  
            actions = actions,  
            colors = TopAppBarDefaults.topAppBarColors(  
                containerColor = Color.Transparent,  
                titleContentColor = Color.Black,  
				navigationIconContentColor = Color.Black, 
                actionIconContentColor = Color.Unspecified
            )  
        )  
    }  
}