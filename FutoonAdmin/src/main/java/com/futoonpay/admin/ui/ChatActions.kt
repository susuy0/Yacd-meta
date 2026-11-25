package com.futoonpay.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.ui.theme.GreenDark
import com.futoonpay.admin.ui.theme.Line

/** Shared business actions. Routes, enabled states, data and click lambdas remain at the call site. */
@Composable
internal fun ChatButton(onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true,
    shape:Shape=RoundedCornerShape(24.dp), colors:ButtonColors=ButtonDefaults.buttonColors(),
    elevation:ButtonElevation?=null, border:BorderStroke?=null,
    contentPadding:PaddingValues=PaddingValues(horizontal=24.dp,vertical=8.dp), interactionSource:MutableInteractionSource?=null,
    content:@Composable RowScope.()->Unit) {
    Button(onClick=onClick,modifier=modifier.heightIn(min=48.dp),enabled=enabled,shape=shape,colors=colors,elevation=elevation,border=border,contentPadding=contentPadding,interactionSource=interactionSource,content=content)
}
@Composable
internal fun ChatOutlinedButton(onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true,
    shape:Shape=RoundedCornerShape(24.dp), colors:ButtonColors=ButtonDefaults.outlinedButtonColors(contentColor=GreenDark),
    elevation:ButtonElevation?=null, border:BorderStroke?=BorderStroke(1.dp,Line),
    contentPadding:PaddingValues=PaddingValues(horizontal=24.dp,vertical=8.dp), interactionSource:MutableInteractionSource?=null,
    content:@Composable RowScope.()->Unit) {
    OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=48.dp),enabled=enabled,shape=shape,colors=colors,elevation=elevation,border=border,contentPadding=contentPadding,interactionSource=interactionSource,content=content)
}
@Composable
internal fun ChatTextButton(onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true,
    shape:Shape=RoundedCornerShape(24.dp), colors:ButtonColors=ButtonDefaults.textButtonColors(contentColor=GreenDark),
    elevation:ButtonElevation?=null, border:BorderStroke?=null,
    contentPadding:PaddingValues=PaddingValues(horizontal=16.dp,vertical=8.dp), interactionSource:MutableInteractionSource?=null,
    content:@Composable RowScope.()->Unit) {
    TextButton(onClick=onClick,modifier=modifier.heightIn(min=48.dp),enabled=enabled,shape=shape,colors=colors,elevation=elevation,border=border,contentPadding=contentPadding,interactionSource=interactionSource,content=content)
}
