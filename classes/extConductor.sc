+Conductor {

	*midiMonitor {

		Conductor.make { | con, control = \SV|
			var updateFunc;
			con.gui.use{ 
				~playerGUI = ~simplePlayerGUI;
				~cvGUI = ~numerical; 
				~svGUI = { | w, name, cv, rect |
					StaticText(w, Rect(0, 0, w.bounds.width - 10, 20) )
						.font_(Font("Courier", 12) )
						.string_(" status   channel  b      c"); 
						w.view.decorator.nextLine;
					SVSync(cv, ListView(w, w.bounds.insetBy(4, 40))
								.resize_(5)
								.font_(Font("Courier", 12) )
								.hiliteColor_(Color.blue(0.2, 0.2) )
					);
				};  
				~listRect = Rect (0, 0, 200, 200);
			};
			con.noSettings;
			con.name_("MIDI Monitor");
		
			control.items = [""];	
			updateFunc = { | selector |
				{
					var ev;
					loop {
						ev = MIDIIn.perform(selector);
						control.items = control.items
						.addFirst(
							ev.status.asString.extend(10, Char.space) 
							+ ev.chan.asString.extend(6, Char.space) 
							+ ev.b.asString.extend(6, Char.space) 
							+ ev.c.asString.extend(6, Char.space))
						[..100];
					}
				}
			};
			con.action_( { MIDIIn.connectAll });
			con.task_( updateFunc.value( \waitControl));
			con.task_( updateFunc.value( \waitNoteOn));
			con.task_( updateFunc.value( \waitNoteOff));
			con.task_( updateFunc.value( \waitPoly));
			con.task_( updateFunc.value( \waitTouch));
			con.task_( updateFunc.value( \waitControl));
			con.task_( updateFunc.value( \waitBend));
			con.task_( updateFunc.value( \waitProgram));
			
		}.show("midi monitor", 30, Window.screenBounds.height - 40, 230, 500 );
	}


	useMIDI { | argKeys |
		var conductor = this;
		if (conductor.valueKeys.includes(\waitControlMappings).not) {
			conductor.valueKeys = conductor.valueKeys ++ \mappings;
			conductor[\waitControlMappings] = Ref( () );
		};
		conductor.gui.guis.put('map MIDI', 
			 { |win, name, interp|
			 	var w;
				~simpleButton.value(win, Rect(0,0,60, 20))
					.states_([["map MIDI", Color.black, Color.hsv(0, 0.5,1)]])
					.action_({ var cond;
						MIDIIn.connectAll;
						if (w.isNil) {
						cond = Conductor.make({ | con |
							var keys, ccAssigns, kdAssigns;
							con.gui.header = [];
							~ccAssigns = ccAssigns = ();
							con.noSettings;
							con.name_("MIDI mapper");
							keys = argKeys ?? { conductor.gui.keys.flat.select{ | k | conductor[k].class === CV } };
							
							keys.do({ | k | con.addCV(k) });
							keys.do({ | k | con[k].sp(0,0,2,1) });
							con.gui.use {
								~cvGUI = ~radiobuttons
							};
							
							
							con.task_( { var ev, packet, activeKeys;
									loop {
									ev = MIDIIn.waitControl;
									packet = ev.chan * 128 + ev.b;
									activeKeys = keys.select { | k | con[k].value == 1 };
									if (activeKeys.size > 0) {
										conductor[\waitControlMappings].value.put(packet, activeKeys.copy);
									defer {
										activeKeys.do { | k | con[k].value = 2 }
									}
								}
							};
						
							
							})
						});
						w = cond.show("MIDImap", win.bounds.left + win.bounds.width, win.bounds.top, 200, 300);
						cond.play;
						defer ({ w.bounds = w.bounds.resizeBy(50, 0)}, 0.02) ;
						topEnvironment[\w] = w;
					} {
						w.close; w = nil;
					};
				});
			}		
		);
		conductor.gui.guis.put(\midi, 
		 { |win, name, interp|
		 	var routine;
			~simpleButton.value(win, Rect(0,0,60, 20))
				.states_([["midi", Color.black, Color.hsv(0, 0.7,1)], ["midi", Color.red, Color.black] ])
				// do this directly in the gui to keep it disconnected from CmdPeriod
				.action_({ |bt |
					if (bt.value == 0) {
						routine.stop; routine.originalStream.stop; routine = nil;
					} {
						MIDIIn.connectAll;
						routine = Task { var ev,keys;
							loop {
								ev = MIDIIn.waitControl;
								if ( (keys = conductor[\waitControlMappings].value[ev.chan * 128 + ev.b]).notNil) {
									keys.do { | key |
										conductor[key].input_(ev.c/127);
									}
								}	
							}
						}.play;
					}
				});
			});			

		conductor.gui.header = [ conductor.gui.header[0] ++ \midi ++ 'map MIDI'];
	}
}