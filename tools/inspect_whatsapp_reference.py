#!/usr/bin/env python3
"""Record static UI measurements from this exact reference APK's decoded resources.

Usage: python3 tools/inspect_whatsapp_reference.py WhatsApp.apk decoded > reference.json
Decode with Apktool 3.0.3: java -jar apktool.jar d -s WhatsApp.apk -o decoded
No executable code, account data, or credentials are copied into the host app.
"""
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

apk, decoded = map(Path, sys.argv[1:])
digest = hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest()
assert digest == '528113bcfcfd3c4686a77bffa8a0b5db890c11b8362bfecbc441844c595bb671', 'Different reference version'
android = '{http://schemas.android.com/apk/res/android}'
res = decoded / 'res'

def values(night=False):
    scalars, styles = {}, {}
    for folder in ['values'] + (['values-night'] if night else []):
        for file in sorted((res / folder).glob('*.xml')):
            for node in ET.parse(file).getroot():
                if node.tag == 'style':
                    styles[node.get('name')] = node
                elif node.get('name'):
                    scalars[node.tag + '/' + node.get('name')] = ''.join(node.itertext())
    return scalars, styles

scalars, styles = values()
def resolve(value, table=scalars):
    seen = set()
    while value.startswith('@') and value[1:] in table and value not in seen:
        seen.add(value)
        value = table[value[1:]]
    return value

def layout(resource, view_id, attribute):
    tree = ET.parse(res / 'layout' / ('APKTOOL_RENAMED_' + resource + '.xml'))
    node = next(n for n in tree.iter() if n.get(android + 'id') == '@id/' + view_id)
    return resolve(node.get(android + attribute))

def style(resource, attribute):
    node = styles['APKTOOL_RENAMED_' + resource]
    while True:
        for item in node:
            if item.get('name') == attribute:
                return resolve(item.text)
        parent = node.get('parent', '').removeprefix('@style/')
        if parent not in styles:
            raise KeyError((resource, attribute))
        node = styles[parent]

measurements = {
    'bottom_navigation.min_height': style('0x7f150668', 'android:minHeight'),
    'bottom_navigation.indicator_width': style('0x7f150669', 'android:width'),
    'bottom_navigation.indicator_height': style('0x7f150669', 'android:height'),
    'bottom_navigation.label_size': style('0x7f1504b5', 'android:textSize'),
    'conversation_row.min_height': layout('0x7f0e06c8', 'contact_row_container', 'minHeight'),
    'conversation_row.avatar_slot_width': layout('0x7f0e06c8', 'contact_selector', 'layout_width'),
    'conversation_row.content_end_padding': layout('0x7f0e06c8', 'conversations_row_content', 'paddingRight'),
    'composer.input_min_height': layout('0x7f0e0509', 'input_layout', 'minHeight'),
    'composer.action_width': resolve(ET.parse(res / 'layout/APKTOOL_RENAMED_0x7f0e0502.xml').getroot().get(android + 'layout_width')),
    'composer.action_height': resolve(ET.parse(res / 'layout/APKTOOL_RENAMED_0x7f0e0502.xml').getroot().get(android + 'layout_height')),
    'composer.input_radius': resolve('@dimen/APKTOOL_RENAMED_0x7f0711b9'),
    'popup.corner_radius': resolve('@dimen/APKTOOL_RENAMED_0x7f071230'),
    'popup.vertical_padding': resolve('@dimen/APKTOOL_RENAMED_0x7f071232'),
}
palettes = {}
for night in [False, True]:
    table, palette_styles = values(night)
    node = palette_styles['APKTOOL_RENAMED_0x7f150546']
    roles = {'accent': '9ad', 'selection': '9ae', 'accent_text': '9ca', 'secondary_text': '9cb', 'primary_text': '9cc', 'outgoing_bubble': '9c1'}
    palettes['dark' if night else 'light'] = {
        name: resolve(next(i.text for i in node if i.get('name') == 'APKTOOL_RENAMED_0x7f040' + suffix), table)
        for name, suffix in roles.items()
    }
print(json.dumps({'package': 'com.whatsapp', 'version': '2.26.42.71', 'bytes': apk.stat().st_size,
                  'sha256': digest, 'basis': 'Static resource values; runtime feature flags can choose alternate layouts.',
                  'measurements': measurements, 'palette': palettes}, indent=2))
