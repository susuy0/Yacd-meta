package org.telegram.messenger.futoon;

public final class FutoonPanelLayoutTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        // Parent height, padding, action bar (including status bar), system navigation / IME.
        int[][] windows={{844,0,92,24},{844,0,92,320},{844,0,92,360},
                {390,0,64,24},{390,0,64,220},{480,0,80,48},{300,12,64,120},{160,0,56,100}};
        for(int[] w:windows){
            int content=FutoonPanelLayout.contentHeight(w[0],w[1],w[2],w[3]);
            int top=w[1]+w[2],bottom=w[0]-w[3];
            check(top+content<=bottom,"Panel must fit below the toolbar and above navigation/IME");
            for(boolean large:new boolean[]{false,true})for(boolean keyboard:new boolean[]{false,true}){
                int sheet=FutoonPanelLayout.sheetHeight(content,8,360,large,keyboard);
                check(sheet>=0&&sheet+16<=Math.max(16,content),"Sheet margins and preferred minimum cannot overflow");
            }
            for(int percent:new int[]{50,85,100}){
                int custom=FutoonPanelLayout.sheetHeight(content,8,240,false,false,percent);
                check(custom>=0&&custom<=Math.max(0,content-16),"Custom height must fit even tiny windows");
            }
        }
        check(FutoonPanelLayout.contentHeight(844,0,92,24)==728,"Pixel portrait usable area");
        check(FutoonPanelLayout.contentHeight(844,0,92,320)==432,"Keyboard replaces navigation inset, not added twice");
        check(FutoonPanelLayout.contentHeight(100,0,92,40)==0,"Oversized insets clamp to zero");
        check(FutoonPanelLayout.sheetHeight(728,8,360,true,false)==712,"Expanded sheet respects both margins");
        check(FutoonPanelLayout.sheetHeight(728,8,240,false,false,50)==356,"50 percent must actually shrink the panel");
        check(FutoonPanelLayout.sheetHeight(728,8,240,false,false,85)==605,"85 percent uses the available area");
        check(FutoonPanelLayout.sheetHeight(728,8,240,false,false,100)==712,"100 percent respects margins");
        check(FutoonPanelLayout.sheetHeight(728,8,240,false,false,200)==712,"Oversized percentage clamps");
        check(FutoonPanelLayout.sheetHeight(728,8,240,false,true,50)==712,"Keyboard always exposes full usable height");
        check(FutoonPanelLayout.sheetHeight(0,8,240,false,false,85)==0,"Empty window stays zero sized");
        // The previous MATCH_PARENT measurement necessarily extended past the screen.
        check(92+844>844-24,"Fixture reproduces old parent-measurement overshoot");
        System.out.println("PASS: "+checks+" native panel geometry checks (portrait, landscape, IME, small windows)");
    }
}
