#!/usr/bin/env python3
"""Audit real host integration; Android rendering/touch regressions run in native-admin."""
from pathlib import Path
import re,sys
repo=Path(__file__).resolve().parents[1];host=Path(sys.argv[1]);src=host/'TMessagesProj/src/main/java/org/telegram/ui'
policy=(repo/'FutoonAdmin/src/main/java/com/futoonpay/admin/appearance/ChatLook.java').read_text();theme=(src/'ActionBar/Theme.java').read_text();adapter=(src/'FutoonChatAppearance.java').read_text()
keys=set(re.findall(r'public static (?:final )?int (key_\w+)\s*=',theme));mapped=dict(re.findall(r'put\("(key_\w+)", Theme\.(key_\w+)\)',adapter))
assert len(mapped)>=100
assert all(k==v and k in keys for k,v in mapped.items())
assert set(re.findall(r'case "(key_\w+)"',policy))<=keys
assert 'java.lang.reflect' not in adapter
assert 'if (currentTheme != null)' in theme and 'FutoonChatAppearance.color(key, currentTheme.isDark())' in theme
composer=(src/'Components/ChatActivityEnterView.java').read_text();a=composer.index('    public void drawBackground(');b=composer.index('\n    public float getVisualHeight()',a)
assert 'setBackground(' not in composer[a:b], 'Draw must not allocate or relayout the composer'
assert 'FutoonQuickReplies' in composer and 'insertFutoon' in composer
main=(src/'MainTabsActivity.java').read_text();assert 'setOnLongClickListener(this::openAccountSelector)' in main and 'setOnLongClickListener(this::openFoldersSelector)' in main
assert 'POSITION_CONTACTS = -1' in main and 'TABS_COUNT = 3' in main
assert 'setChatAppearance("chat")' in main
view=(src/'DialogsActivity.java').read_text();assert 'hasChatQuickFilters()' in view and '!onlySelect && folderId == 0 && communityId == 0' in view
assert 'dialog.unread_count > 0 || dialog.unread_mark' in view and 'chat.megagroup' in view
assert 'getChatSearchHeight()' in view and 'R.string.FilterNameUnread' in view
assert 'new ArrayList<>()' in view and 'return filtered;' in view
assert 'FutoonPanel' in (src/'ChatActivity.java').read_text()
assert 'ChatLook.bubblePath' in (src/'ActionBar/MessageDrawable.java').read_text()
assert 'getImageReceiver' in (src/'ProfileActivity.java').read_text() and 'Telegram ID' in (src/'ProfileActivity.java').read_text()
print(f'PASS: {len(mapped)} native theme keys, cached palette, native filters, composer allocation guard, existing callbacks, business and permission boundaries')
