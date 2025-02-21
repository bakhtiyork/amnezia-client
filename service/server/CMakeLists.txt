cmake_minimum_required(VERSION 3.25.0 FATAL_ERROR)

set(PROJECT AmneziaVPN-service)
project(${PROJECT})

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(Qt6 REQUIRED COMPONENTS Core Network RemoteObjects Core5Compat)
qt_standard_project_setup()

set(HEADERS
        ${CMAKE_CURRENT_LIST_DIR}/../../client/utilities.h
        ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipc.h
        ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipcserver.h
        ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipcserverprocess.h
        ${CMAKE_CURRENT_LIST_DIR}/localserver.h
        ${CMAKE_CURRENT_LIST_DIR}/logger.h
        ${CMAKE_CURRENT_LIST_DIR}/router.h
        ${CMAKE_CURRENT_LIST_DIR}/systemservice.h
)

set(SOURCES
        ${CMAKE_CURRENT_LIST_DIR}/../../client/utilities.cpp
        ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipcserver.cpp
        ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipcserverprocess.cpp
        ${CMAKE_CURRENT_LIST_DIR}/localserver.cpp
        ${CMAKE_CURRENT_LIST_DIR}/logger.cpp
        ${CMAKE_CURRENT_LIST_DIR}/main.cpp
        ${CMAKE_CURRENT_LIST_DIR}/router.cpp
        ${CMAKE_CURRENT_LIST_DIR}/systemservice.cpp
)

if(WIN32)
    set(HEADERS ${HEADERS}
        ${CMAKE_CURRENT_LIST_DIR}/tapcontroller_win.h
        ${CMAKE_CURRENT_LIST_DIR}/router_win.h
    )

    set(SOURCES ${SOURCES}
        ${CMAKE_CURRENT_LIST_DIR}/tapcontroller_win.cpp
        ${CMAKE_CURRENT_LIST_DIR}/router_win.cpp
    )

    set(LIBS
        user32
        rasapi32
        shlwapi
        iphlpapi
        ws2_32
        iphlpapi
        gdi32
        Advapi32
        Kernel32
    )

    add_compile_definitions(_WINSOCKAPI_)
endif()

if(APPLE)
    set(HEADERS ${HEADERS}
        ${CMAKE_CURRENT_LIST_DIR}/helper_route_mac.h
        ${CMAKE_CURRENT_LIST_DIR}/router_mac.h
    )

    set(SOURCES ${SOURCES}
        ${CMAKE_CURRENT_LIST_DIR}/helper_route_mac.c
        ${CMAKE_CURRENT_LIST_DIR}/router_mac.cpp
    )
endif()

if(LINUX)
    set(HEADERS ${HEADERS}
        ${CMAKE_CURRENT_LIST_DIR}/router_linux.h
    )

    set(SOURCES ${SOURCES}
        ${CMAKE_CURRENT_LIST_DIR}/router_linux.cpp
    )
endif()

include(${CMAKE_CURRENT_LIST_DIR}/../src/qtservice.cmake)

include_directories(
    ${CMAKE_CURRENT_LIST_DIR}
    ${CMAKE_CURRENT_LIST_DIR}/../../client
    ${CMAKE_CURRENT_LIST_DIR}/../../ipc
    ${CMAKE_CURRENT_BINARY_DIR}
)

add_executable(${PROJECT} ${SOURCES} ${HEADERS})
target_link_libraries(${PROJECT} PRIVATE Qt6::Core Qt6::Network Qt6::RemoteObjects Qt6::Core5Compat ${LIBS})

qt_add_repc_sources(${PROJECT} ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipc_interface.rep)
if(NOT IOS)
    qt_add_repc_sources(${PROJECT} ${CMAKE_CURRENT_LIST_DIR}/../../ipc/ipc_process_interface.rep)
endif()

# deploy artifacts required to run the application to the debug build folder
if(WIN32)
    if("${CMAKE_SIZEOF_VOID_P}" STREQUAL "8")
        set(DEPLOY_ARTIFACT_PATH "windows/x64")
    else()
        set(DEPLOY_ARTIFACT_PATH "windows/x32")
    endif()
elseif(LINUX)
    set(DEPLOY_ARTIFACT_PATH "linux/service")
elseif(APPLE AND NOT IOS)
    set(DEPLOY_ARTIFACT_PATH "macos")
endif()

if(NOT IOS AND NOT ANDROID)
    add_custom_command(
        TARGET ${PROJECT} POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E $<IF:$<CONFIG:Debug>,copy_directory,true>
        ${CMAKE_SOURCE_DIR}/deploy/data/${DEPLOY_ARTIFACT_PATH}
        $<TARGET_FILE_DIR:${PROJECT}>
        COMMAND_EXPAND_LISTS
    )
endif()
