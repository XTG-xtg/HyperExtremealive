import com.frederic22.hyperextreme.alive.core.AliveState;
import com.frederic22.hyperextreme.alive.core.PhotoPowerGate;

public final class PhotoPowerStateTest {
    static int count;
    static void check(boolean ok,String reason){count++;if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args) {
        if(args.length>0) {
            AliveState old=new AliveState();old.accept(2,0,"user-present");
            old.accept(0,1000,"screen-off");old.accept(1,1038,"visible-slot-2");
            check(old.current==1,"v8 trace must reproduce the wrong expansion");
            System.out.println("V8_REPRODUCED screen-off -> visible-slot-2 changed AOD to LOCK after 38ms");return;
        }
        PhotoPowerGate gate=new PhotoPowerGate();gate.reset(true);
        check(gate.resolve(0,1000,"screen-off",true)==0,"sleep prefeed");
        check(gate.resolve(1,1038,"visible-slot-2",true)==0,"visible during sleep must not expand");
        check(gate.resolve(0,1118,"screen-off",false)==0,"broadcast");
        check(gate.resolve(1,1151,"scrim-keyguard",false)==0,"keyguard underneath AOD");
        check(gate.resolve(0,1191,"scrim-aod",false)==0,"doze");
        for(int next:new int[]{1,2})for(String reason:new String[]{"visible-slot-1","visible-slot-2","scrim-keyguard","scrim-unlocked","power-wake","biometric","screen-on","user-present"})
            check(gate.resolve(next,1300,reason,false)==0,"noninteractive "+reason);
        check(gate.resolve(1,1500,"screen-on",true)==1,"interactive lock wake");
        check(!gate.sleeping(),"wake clears fence");
        check(gate.resolve(0,1501,"scrim-aod",true)==-1,"late AOD scrim cannot undo an interactive wake");
        check(gate.resolve(0,1502,"visible-slot-2",true)==-1,"old doze display sample after wake");
        check(!gate.sleeping(),"late doze hints cannot rearm fence");
        check(gate.resolve(2,1600,"user-present",true)==2,"unlock");
        check(gate.resolve(0,1550,"screen-off",false)==-1,"late sleep rejected");
        check(!gate.sleeping(),"late sleep cannot arm fence");
        for(int cycle=0;cycle<30;cycle++) {
            long at=2000+cycle*1000;
            check(gate.resolve(0,at,"screen-off",true)==0,"repeat sleep");
            check(gate.resolve(1,at+10,"scrim-keyguard",true)==0,"repeat transitional scrim");
            check(gate.resolve(1,at+100,"power-wake",false)==0,"ambient wake is not interactive");
            check(gate.resolve(1,at+200,"display-awake",true)==1,"actual display wake");
            check(gate.resolve(0,at+300,"screen-off",true)==0,"sleep while opening");
            check(gate.resolve(2,at+310,"visible-slot-1",true)==0,"late home visibility");
            check(gate.resolve(2,at+700,"biometric",true)==2,"interactive fingerprint");
        }
        check(gate.resolve(7,50000,"bad",false)==-1,"invalid event");
        check(!gate.sleeping(),"invalid event cannot arm fence");
        gate.reset(false);
        check(gate.resolve(1,1,"visible-slot-2",false)==0,"recreated AOD");
        check(gate.resolve(1,2,"wallpaper-waking",true)==1,"standard wallpaper wake");
        combinedReducer();
        System.out.println("PASS "+count+" photo power assertions");
    }

    static void combinedReducer() {
        PhotoPowerGate gate=new PhotoPowerGate();gate.reset(true);
        AliveState state=new AliveState();state.accept(2,0,"user-present");
        state.accept(gate.resolve(0,1000,"screen-off",true),1000,"screen-off");
        long collapse=state.sequence;
        // Both slots and scrim may keep emitting hints while an AOD panel is lit.
        for(int i=0;i<100;i++) {
            long at=1038+i*100;
            String reason=i%2==0?"visible-slot-2":"scrim-keyguard";
            boolean changed=state.accept(gate.resolve(1,at,reason,i==0),at,reason);
            check(!changed&&state.current==AliveState.AOD,"AOD must stay collapsed for ten seconds");
            check(state.sequence==collapse&&state.startedMs==1000,"duplicate AOD must not restart collapse");
        }
        check(state.accept(gate.resolve(1,12000,"screen-on",true),12000,"screen-on"),"interactive wake reaches reducer");
        check(state.previous==AliveState.AOD&&state.current==AliveState.LOCK,"wake expands from AOD");
        check(!state.accept(gate.resolve(0,12001,"scrim-aod",true),12001,"scrim-aod"),"late doze does not interrupt opening");
        check(state.accept(gate.resolve(0,12100,"screen-off",true),12100,"screen-off"),"power interrupts opening");
        check(state.previous==AliveState.LOCK&&state.current==AliveState.AOD,"lock-to-sleep path reaches reducer");
        check(!state.accept(gate.resolve(2,12110,"visible-slot-1",true),12110,"visible-slot-1"),"home visibility cannot reverse collapse");
        check(!state.accept(gate.resolve(1,12200,"power-wake",false),12200,"power-wake"),"ambient pulse cannot expand");
        check(state.accept(gate.resolve(2,13000,"biometric",true),13000,"biometric"),"real fingerprint unlock reaches home");
        check(state.current==AliveState.HOME,"fingerprint target");
        check(!state.accept(gate.resolve(1,13010,"scrim-keyguard",true),13010,"scrim-keyguard"),"legacy unlock guard remains active");
        check(state.current==AliveState.HOME,"late keyguard cannot undo unlock");
    }
}
