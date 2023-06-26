# one clog file, and one or more wlog files
clog_file = 'clog_p6.csv'
wlog_files = ['wlog_oppo.csv', 'wlog_p6.csv']
out_file = 'out.csv'

# logs: key with frame number
logs = {}

with open(clog_file, encoding='utf-8') as f:
    # drop the first line, we need to adjust it every time anyway
    f.readline()

    try:
        while True:
            s = f.readline().split(',')
            fn = int(s[0])
            logs[fn] = list(map(int, s))
    except ValueError:
        pass
    except:
        print('Loop not terminated by ValueError, something is wrong')
        exit(-1)

# same logic as clog but for possibly multiple files
for fname in wlog_files:
    with open(fname, encoding='utf-8') as f:
        f.readline()

        try:
            while True:
                s = f.readline().split(',')
                fn = int(s[0])

                ls = logs[fn]  # so that we don't access to the dict repeatedly

                x=ls[-1]  # save total time and put it back later
                ls.pop()
                ls.pop()  # let's not use 'wait for result' for now
                ls += list(map(int, s[1:-1]))  # exclude frame number and  total time
                ls.append(x - sum(ls[1:]))  # Estimated network time (TODO: remove this when I can actually estimate it)
                ls.append(x)  # total time
        except ValueError:
            pass
        except:
            print('Loop not terminated by ValueError, something is wrong')
            exit(-1)

with open(out_file, 'w', encoding='utf-8') as f:
    f.write('Frame Number,Distribution Algorithm,Send to Communicator,Send to Worker,Send to WorkerThread,Notify Availability,Uncompress Bitmap,Run FrameProcessor,Start Sending Result,Return Result to Coordinator,Network,Total\n')
    ls=[]
    for k in logs.keys():
        ls.append(logs[k])
    ls.sort()
    for data in ls:
        if len(data) != 12:  # incomplete test
            continue
        f.write(str(data)[1:-1].replace(' ', '') + '\n')
