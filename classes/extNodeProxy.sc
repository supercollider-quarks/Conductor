+NodeProxy {
	set { arg ... args; // pairs of keys or indices and value
		nodeMap.set(*args);
		if(this.isPlaying) { 
			server.sendBundle(server.latency, [15, group.nodeID] ++ args); 
		};
		this.changed(\set, args);
	}
	
	setn { arg ... args;
		nodeMap.set(*args);
		if(this.isPlaying) { 
			server.sendBundle(server.latency, group.setnMsg(*args)); 
		};
		this.changed(\setn, args);
	}
	prset { arg ... args; // pairs of keys or indices and value
		nodeMap.set(*args);
		if(this.isPlaying) { 
			server.sendBundle(server.latency, [15, group.nodeID] ++ args); 
		};
	}
	
	prsetn { arg ... args;
		nodeMap.set(*args);
		if(this.isPlaying) { 
			server.sendBundle(server.latency, group.setnMsg(*args)); 
		};
	}
	setControls { | args | 
		args.buildCVConnections(
			{ | label, expr| this.prset(label, expr.value)}, 
			{ | cvLinks|
				OSCpathResponder(group.server.addr, ["/n_end", group.nodeID], 
					{ arg time, resp, msg; cvLinks.do({ arg n; n.remove}); resp.remove;} 
				).add;
			}
		)
		.do { | pair | this.prset(pair[0], pair[1]) }
		;
	}

	conduct { | argKeys, prefs, func |
		var con, np, keys;
		var topW;
		np = this;

		con = Conductor.make { | con | 
			if (prefs.notNil) { con.gui.putAll(prefs) };  
			func.value;
			~mappings = Ref( () );
			con.valueKeys = #[mappings];
			con.task_({ var ev,keys;
				loop {
					ev = MIDIIn.waitControl;
					if ( (keys = con[\mappings].value[ev.chan * 128 + ev.b]).notNil) {
						keys.do { | key |
							con[key].input_(ev.c/127);
						}
					}	
				}
			});
			con.gui.header = #[ [ player, settings, preset, midi, midiMap ] ];

		con.gui.guis.put(\midiMap, 
			 { |win, name, interp|
			 	var w;
				var c = currentEnvironment.conductor.postln;
				~simpleButton.value(win, Rect(0,0,60, 20))
					.states_([["midiMap", Color.black, Color.hsv(0, 0.5,1)]])
					.action_({ var cond;
						MIDIIn.connectAll;
						if (w.isNil) {
						cond = Conductor.make({ | con |
							var keys, ccAssigns, kdAssigns;
							con.gui.header = [];
							~ccAssigns = ccAssigns = ();
							con.noSettings;
							con.name_("MIDI mapper");
							keys = c.gui.keys.select{ | k | c[k].class === CV };
							
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
										c[\mappings].value.put(packet, activeKeys.copy);
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
		

	con.gui.guis.put(\midi, 
		 { |win, name, interp|
		 	var routine;
			var con = currentEnvironment.conductor.postln;
			~simpleButton.value(win, Rect(0,0,60, 20))
				.states_([["midi", Color.black, Color.hsv(0, 0.7,1)], ["midi", Color.red, Color.black] ])
				.action_({ |bt |
					if (bt.value == 0) {
						routine.stop; routine.originalStream.stop; routine = nil;
					} {
						MIDIIn.connectAll;
						routine = Task { var ev,keys;
							loop {
								ev = MIDIIn.waitControl;
								if ( (keys = con[\mappings].value[ev.chan * 128 + ev.b]).notNil) {
									keys.do { | key |
										con[key].input_(ev.c/127);
									}
								}	
							}
						}.play;
					}
				});
			});			



		};
		con.nodeProxy_(this);
		con[\np] = np;
		con[\npControl] = SimpleController(this).put(\set,
			{ | obj, cmd, kV | 
				var topW;
//				con.updateNPCV(np, *kV); 
				np.updateCV(con, *kV); 
				topW = Document.current;
				con.gui.resize; 
				if (topW.notNil) { topW.front };
			});
		keys = nodeMap.settings.keys.asArray;
		keys.remove(\i_out);
		keys.remove(\out);
		keys.remove(\in);
		keys.remove(\fin);
		keys.do { | k |
//			 con.updateNPCV( np,  k, nodeMap.settings[k].value) 
			 np.updateCV( con,  k, nodeMap.settings[k].value);
		};
		argKeys.do { | k | 
			np.updateCV( con,  k) ;
//			con.updateNPCV( np,  k) 
		};
		
		topW = Document.current;
		con.show;
		defer({ topW.front }, 0.025);
		^con
	}
	
	updateCV {| con, key, value |
		var cv, np;
		np = this;
		if ( (cv = con[key]).notNil) { 
			if (value.notNil) { cv.value_(value) }
		} {
			cv = con.addCV(key, value);
			cv.action_({ np.prset(key, cv.value) });
			this.prset(key, cv.value);
		}		
	}
		
	linkNodeMap {
		var index;
		index = this.index;
		if(index.notNil) { nodeMap.set(\out, index, \i_out, index, \in, index, \fin, index) };
		nodeMap.proxy = this;
	}
	
	
}