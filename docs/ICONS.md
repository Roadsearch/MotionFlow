# Icônes VectorDrawable

132 icônes vectorielles dans `app/src/main/res/drawable/` (54 propres à l'éditeur, 78 Material).

## Nommage
- `ic_<nom>` : icônes de l'éditeur. Couleurs d'origine conservées (surtout `#EFEFF4`, prévues pour un fond sombre).
- `ic_m_<nom>` : icônes Material (Apache-2.0), en noir. À teinter avec `tint`.

## Utilisation (Compose)
```kotlin
Icon(painterResource(R.drawable.ic_add_effect), contentDescription = null)
Icon(painterResource(R.drawable.ic_m_add), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
```

## Notes
- Converties depuis des SVG. Les `clipPath` plein cadre ont été retirés (sans effet visuel).
- Rendu vérifié contre les SVG d'origine : écart maximal de 0,4 %.
- `ic_export`, `ic_launcher_*` et les autres ressources existantes ne sont pas touchées.

## Liste
Éditeur :
`ic_add_effect, ic_add_keyframe_2, ic_add_keyframe_3, ic_add_music, ic_add_text, ic_album_video, ic_audio_right_play_icon, ic_auto_captions, ic_camera_selected, ic_caption_font, ic_caption_style, ic_clip_speed, ic_crop, ic_del_keyframe_2, ic_del_keyframe_3, ic_export_captions, ic_export_settings_arrow, ic_filter_edit, ic_home, ic_home_template_menu_un, ic_media_add_unselect, ic_menu_ai_effects, ic_menu_music, ic_menu_text, ic_menu_trim, ic_menu_volume, ic_mirror, ic_music_voiceover_white, ic_normal_ratio, ic_play_arrow, ic_redo, ic_setting, ic_setting_feed_back, ic_setting_purchase_restore, ic_text_add, ic_text_to_speech, ic_tool_extract_audio, ic_toolbox_ai_upscaler, ic_toolbox_ai_video, ic_toolbox_ai_writing, ic_toolbox_auto_cut, ic_toolbox_batch_trim, ic_toolbox_camera, ic_toolbox_compress, ic_toolbox_more_tools, ic_toolbox_remove_bg, ic_toolbox_retouch, ic_toolbox_script_cut, ic_toolbox_script_to_video, ic_toolbox_speed, ic_toolbox_teleprompter, ic_toolbox_tts, ic_undo, ic_vip_product_selected`

Material :
`ic_m_add, ic_m_add_circle, ic_m_add_photo_alternate, ic_m_animation, ic_m_arrow_back, ic_m_auto_awesome, ic_m_auto_awesome_mosaic, ic_m_auto_fix_high, ic_m_blur_on, ic_m_bookmark, ic_m_call_split, ic_m_check, ic_m_close, ic_m_closed_caption, ic_m_content_copy, ic_m_content_cut, ic_m_content_paste, ic_m_crop, ic_m_delete_outline, ic_m_drag_handle, ic_m_edit, ic_m_file_upload, ic_m_filter_vintage, ic_m_first_page, ic_m_flag, ic_m_flip, ic_m_folder_open, ic_m_fullscreen, ic_m_gradient, ic_m_help_outline, ic_m_home, ic_m_image, ic_m_info, ic_m_ios_share, ic_m_keyboard_arrow_down, ic_m_keyboard_arrow_up, ic_m_last_page, ic_m_layers, ic_m_link, ic_m_link_off, ic_m_lock, ic_m_lock_open, ic_m_mic, ic_m_more_horiz, ic_m_more_vert, ic_m_movie, ic_m_music_note, ic_m_opacity, ic_m_palette, ic_m_pause, ic_m_photo_camera, ic_m_photo_filter, ic_m_photo_library, ic_m_play_arrow, ic_m_redo, ic_m_restart_alt, ic_m_rotate_right, ic_m_save, ic_m_settings, ic_m_share, ic_m_skip_next, ic_m_skip_previous, ic_m_snooze, ic_m_speed, ic_m_swap_horiz, ic_m_text_fields, ic_m_timer, ic_m_title, ic_m_tune, ic_m_undo, ic_m_vertical_align_center, ic_m_video_library, ic_m_visibility, ic_m_visibility_off, ic_m_volume_off, ic_m_volume_up, ic_m_zoom_in, ic_m_zoom_out`
